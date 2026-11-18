-- =====================================================================
-- Shardbound — 01_schema.sql
-- Types and tables. Run on an EMPTY database:
--   psql -U postgres -d shardbound -f database/01_schema.sql
--
-- Two groups of tables:
--   * STATIC CONTENT  — written only by ContentImporter from content/*.json
--   * PLAYER STATE    — written by the game (profiles, saves, achievements)
-- Every table is in 3NF. Derived values (max hp, atk, def, spd, max energy)
-- are computed in Java from level and are never stored (D6, D28).
-- Decision tags (Dn / Sn) refer to docs/DECISIONS.md.
-- =====================================================================

-- ===== TYPES =========================================================
-- ENUM order matters: PostgreSQL compares enum values by declaration order,
-- so ORDER BY tier DESC puts SSS first. Java's Tier and QuestStatus enums
-- use the same order (checked by QuestStatusOrderTest, D13).
CREATE TYPE weapon_tier  AS ENUM ('F','E','D','C','B','A','S','SS','SSS');
CREATE TYPE node_type    AS ENUM ('STORY','COMBAT','RESOLVER','ENDING');
CREATE TYPE item_type    AS ENUM ('WEAPON','CONSUMABLE','KEY');
CREATE TYPE quest_status AS ENUM ('LOCKED','AVAILABLE','ACTIVE','COMPLETED','FAILED');

-- ===== STATIC CONTENT ================================================

CREATE TABLE faction (
  faction_id VARCHAR(20) PRIMARY KEY,
  name       VARCHAR(60) NOT NULL
);

CREATE TABLE location (
  location_id VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  region      VARCHAR(10) NOT NULL
              CHECK (region IN ('VILLAGE','FOREST','DUNGEON','KINGDOM','SPIRE'))   -- D19
);

CREATE TABLE npc (
  npc_id      VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(60) NOT NULL,
  faction_id  VARCHAR(20) REFERENCES faction,      -- nullable: not every NPC has a faction
  location_id VARCHAR(40) REFERENCES location
);

-- Generalization / specialization (ISA): every item has one row here, and
-- weapons/consumables have one extra row in their subtype table that shares
-- the primary key. KEY items need no subtype table.
CREATE TABLE item (
  item_id     VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  type        item_type   NOT NULL,
  description TEXT        NOT NULL,
  price       INT         NOT NULL DEFAULT 0 CHECK (price >= 0)
);

CREATE TABLE weapon (
  item_id  VARCHAR(40) PRIMARY KEY REFERENCES item ON DELETE CASCADE,
  tier     weapon_tier NOT NULL,
  attack   INT NOT NULL CHECK (attack > 0),
  crit_pct INT NOT NULL CHECK (crit_pct BETWEEN 0 AND 100),
  ability  VARCHAR(80)
);

CREATE TABLE consumable (
  item_id VARCHAR(40) PRIMARY KEY REFERENCES item ON DELETE CASCADE,
  heal    INT NOT NULL DEFAULT 0 CHECK (heal >= 0),
  energy  INT NOT NULL DEFAULT 0 CHECK (energy >= 0)
);

CREATE TABLE enemy (
  enemy_id    VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(60) NOT NULL,
  hp          INT NOT NULL CHECK (hp > 0),
  atk         INT NOT NULL CHECK (atk >= 0),
  def         INT NOT NULL CHECK (def >= 0),
  spd         INT NOT NULL CHECK (spd >= 0),
  xp_reward   INT NOT NULL CHECK (xp_reward >= 0),
  gold_reward INT NOT NULL CHECK (gold_reward >= 0),
  ai          VARCHAR(12) NOT NULL CHECK (ai IN ('AGGRESSIVE','DEFENSIVE','BOSS'))
);

CREATE TABLE encounter (
  encounter_id VARCHAR(40) PRIMARY KEY,
  name         VARCHAR(80) NOT NULL,
  can_flee     BOOLEAN NOT NULL DEFAULT TRUE
);

-- Associative table: which enemies (and how many) appear in an encounter.
CREATE TABLE encounter_enemy (
  encounter_id VARCHAR(40) REFERENCES encounter ON DELETE CASCADE,
  enemy_id     VARCHAR(40) REFERENCES enemy,
  qty          SMALLINT NOT NULL CHECK (qty BETWEEN 1 AND 4),
  PRIMARY KEY (encounter_id, enemy_id)
);

-- Story graph vertices. win/lose/flee point at other nodes, possibly ones
-- inserted later in the same transaction, so those FKs are DEFERRABLE and
-- only checked at COMMIT.
CREATE TABLE story_node (
  node_id      VARCHAR(40) PRIMARY KEY,
  chapter      SMALLINT NOT NULL CHECK (chapter BETWEEN 0 AND 5),
  location_id  VARCHAR(40) NOT NULL REFERENCES location,
  type         node_type NOT NULL,
  title        VARCHAR(120) NOT NULL,
  body         TEXT NOT NULL,
  encounter_id VARCHAR(40) REFERENCES encounter,
  win_node     VARCHAR(40) REFERENCES story_node DEFERRABLE INITIALLY DEFERRED,
  lose_node    VARCHAR(40) REFERENCES story_node DEFERRABLE INITIALLY DEFERRED,
  flee_node    VARCHAR(40) REFERENCES story_node DEFERRABLE INITIALLY DEFERRED,  -- D9: NULL = flee goes to lose_node
  -- A COMBAT node must say what is fought and where each outcome leads.
  CHECK (type <> 'COMBAT' OR (encounter_id IS NOT NULL AND win_node IS NOT NULL AND lose_node IS NOT NULL)),
  -- Only COMBAT nodes may have a flee destination (D9, D31).
  CHECK (flee_node IS NULL OR type = 'COMBAT')
);

-- Story graph edges. choice_id stays stable across re-imports because the
-- importer upserts on the natural key (from_node, sort_order) (D1).
CREATE TABLE choice (
  choice_id          SERIAL PRIMARY KEY,
  from_node          VARCHAR(40) NOT NULL REFERENCES story_node ON DELETE CASCADE,
  to_node            VARCHAR(40) NOT NULL REFERENCES story_node DEFERRABLE INITIALLY DEFERRED,
  label              VARCHAR(160) NOT NULL,
  sort_order         SMALLINT NOT NULL,
  hidden_when_locked BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (from_node, sort_order)
);

-- Conditions in disjunctive normal form (D4): rows with the same grp are
-- ANDed together; different grps are ORed. No rows = always available.
CREATE TABLE choice_condition (
  choice_id INT REFERENCES choice ON DELETE CASCADE,
  seq       SMALLINT,
  grp       SMALLINT NOT NULL DEFAULT 0,
  cond_type VARCHAR(10) NOT NULL CHECK (cond_type IN ('FLAG','REP','AFFINITY','ITEM','QUEST','STAT')),
  key       VARCHAR(40) NOT NULL,
  op        VARCHAR(2)  NOT NULL CHECK (op IN ('=','!=','>','>=','<','<=')),
  value     INT NOT NULL,           -- QUEST: ordinal of quest_status (D13)
  PRIMARY KEY (choice_id, seq)
);

CREATE TABLE choice_effect (
  choice_id   INT REFERENCES choice ON DELETE CASCADE,
  seq         SMALLINT,
  effect_type VARCHAR(16) NOT NULL CHECK (effect_type IN
     ('SET_FLAG','REP','AFFINITY','GIVE_ITEM','TAKE_ITEM','START_QUEST',
      'COMPLETE_QUEST','FAIL_QUEST','HEAL','GOLD','XP')),
  key   VARCHAR(40) NOT NULL,
  value INT NOT NULL DEFAULT 0,
  PRIMARY KEY (choice_id, seq)
);

CREATE TABLE quest (
  quest_id    VARCHAR(40) PRIMARY KEY,
  title       VARCHAR(80) NOT NULL,
  description TEXT NOT NULL,
  giver_npc   VARCHAR(40) REFERENCES npc,
  reward_item VARCHAR(40) REFERENCES item,
  reward_xp   INT NOT NULL DEFAULT 0 CHECK (reward_xp >= 0),
  reward_gold INT NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
  is_main     BOOLEAN NOT NULL DEFAULT FALSE
);

-- Quest dependency DAG (edges requires_id -> quest_id). Cycles are rejected
-- in Java by Kahn's algorithm (QuestManager.topoOrder); self-loops here.
CREATE TABLE quest_prereq (
  quest_id    VARCHAR(40) REFERENCES quest ON DELETE CASCADE,
  requires_id VARCHAR(40) REFERENCES quest ON DELETE CASCADE,
  PRIMARY KEY (quest_id, requires_id),
  CHECK (quest_id <> requires_id)
);

CREATE TABLE achievement (
  achievement_id VARCHAR(40) PRIMARY KEY,
  title          VARCHAR(80) NOT NULL,
  description    TEXT NOT NULL,
  hidden         BOOLEAN NOT NULL DEFAULT FALSE
);

-- Convention: an ending's node_id equals its ending_id (e.g. 'END_QUICK').
CREATE TABLE ending (
  ending_id    VARCHAR(40) PRIMARY KEY,
  node_id      VARCHAR(40) NOT NULL UNIQUE REFERENCES story_node,
  title        VARCHAR(80) NOT NULL,
  priority     SMALLINT NOT NULL UNIQUE,         -- lower = checked first by EndingResolver
  via_resolver BOOLEAN  NOT NULL DEFAULT TRUE    -- D11: FALSE = reached directly (END_QUICK)
);

CREATE TABLE ending_condition (
  ending_id VARCHAR(40) REFERENCES ending ON DELETE CASCADE,
  seq       SMALLINT,
  grp       SMALLINT NOT NULL DEFAULT 0,        -- D4: same grp = AND, different grp = OR
  cond_type VARCHAR(10) NOT NULL CHECK (cond_type IN ('FLAG','REP','AFFINITY','ITEM','QUEST','STAT')),
  key       VARCHAR(40) NOT NULL,
  op        VARCHAR(2)  NOT NULL CHECK (op IN ('=','!=','>','>=','<','<=')),
  value     INT NOT NULL,
  PRIMARY KEY (ending_id, seq)
);

-- ===== DYNAMIC PLAYER STATE ==========================================
-- Every FK from a player table to a content table is ON DELETE RESTRICT,
-- except save_history.choice_id (ON DELETE SET NULL). ContentImporter can
-- therefore remove content that left the JSON, but never content that a
-- save still points at (D1).

CREATE TABLE player (
  player_id  SERIAL PRIMARY KEY,
  username   VARCHAR(30) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE save_slot (
  save_id      SERIAL PRIMARY KEY,
  player_id    INT NOT NULL REFERENCES player ON DELETE CASCADE,
  slot_no      SMALLINT NOT NULL CHECK (slot_no BETWEEN 0 AND 3),   -- 0 = autosave, 1..3 manual
  current_node VARCHAR(40) NOT NULL REFERENCES story_node ON DELETE RESTRICT,
  hp           INT NOT NULL,
  energy       INT NOT NULL CHECK (energy >= 0),                    -- D32
  level        INT NOT NULL CHECK (level >= 1),
  xp           INT NOT NULL,          -- progress within the current level, reset on level-up (D29)
  gold         INT NOT NULL CHECK (gold >= 0),
  rng_seed     BIGINT NOT NULL,       -- the fresh seed drawn at save time (D7)
  demo_mode    BOOLEAN NOT NULL DEFAULT FALSE,                      -- D25
  play_seconds INT NOT NULL DEFAULT 0 CHECK (play_seconds >= 0),
  version      INT NOT NULL DEFAULT 1,                              -- +1 on every overwrite
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),                  -- maintained by trigger touch_save
  UNIQUE (player_id, slot_no),
  -- D28: max hp is not stored; the bound is computed from level in the same
  -- row. Same formula as Stats.maxHp(); MaxHpFormulaTest keeps them in sync.
  CHECK (hp BETWEEN 0 AND 40 + 5 * (level - 1))
);

CREATE TABLE save_inventory (
  save_id  INT REFERENCES save_slot ON DELETE CASCADE,
  item_id  VARCHAR(40) REFERENCES item ON DELETE RESTRICT,
  qty      INT NOT NULL CHECK (qty > 0),
  equipped BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (save_id, item_id)
);

-- Flags whose key starts with '_' are reserved for the engine (D15).
CREATE TABLE save_flag (
  save_id  INT REFERENCES save_slot ON DELETE CASCADE,
  flag_key VARCHAR(40),
  value    INT NOT NULL,
  PRIMARY KEY (save_id, flag_key)
);

CREATE TABLE save_reputation (
  save_id    INT REFERENCES save_slot ON DELETE CASCADE,
  faction_id VARCHAR(20) REFERENCES faction ON DELETE RESTRICT,
  value      INT NOT NULL CHECK (value BETWEEN -100 AND 100),
  PRIMARY KEY (save_id, faction_id)
);

CREATE TABLE save_affinity (
  save_id INT REFERENCES save_slot ON DELETE CASCADE,
  npc_id  VARCHAR(40) REFERENCES npc ON DELETE RESTRICT,
  value   INT NOT NULL CHECK (value BETWEEN -100 AND 100),
  PRIMARY KEY (save_id, npc_id)
);

CREATE TABLE save_quest (
  save_id  INT REFERENCES save_slot ON DELETE CASCADE,
  quest_id VARCHAR(40) REFERENCES quest ON DELETE RESTRICT,
  status   quest_status NOT NULL,
  PRIMARY KEY (save_id, quest_id)
);

-- step_no 1 = oldest step (S7).
CREATE TABLE save_history (
  save_id   INT REFERENCES save_slot ON DELETE CASCADE,
  step_no   INT,
  node_id   VARCHAR(40) REFERENCES story_node ON DELETE RESTRICT,
  choice_id INT REFERENCES choice ON DELETE SET NULL,   -- D1: a removed choice only blanks a journal label
  PRIMARY KEY (save_id, step_no)
);

-- Profile-level progress: survives deleting a save.
CREATE TABLE player_achievement (
  player_id      INT REFERENCES player ON DELETE CASCADE,
  achievement_id VARCHAR(40) REFERENCES achievement ON DELETE RESTRICT,
  unlocked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (player_id, achievement_id)
);

CREATE TABLE player_ending (
  player_id     INT REFERENCES player ON DELETE CASCADE,
  ending_id     VARCHAR(40) REFERENCES ending ON DELETE RESTRICT,
  first_reached TIMESTAMPTZ NOT NULL DEFAULT now(),
  best_seconds  INT NOT NULL CHECK (best_seconds >= 0),
  PRIMARY KEY (player_id, ending_id)
);

-- Written only by trigger audit_save. No FK on purpose: the audit trail
-- must survive the deletion of the save it describes.
CREATE TABLE save_audit (
  audit_id SERIAL PRIMARY KEY,
  save_id  INT,
  action   VARCHAR(10),
  node_id  VARCHAR(40),
  at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
