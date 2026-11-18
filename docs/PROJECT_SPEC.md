# Shardbound — Project Specification

> **v1 specification.** For v2 work, `docs/V2_SPEC.md` is the source of truth and overrides this document wherever they differ; everything not overridden (e.g. JDBC conventions, ConditionGroup, quest state machine, D1–D32) still applies.

> Source of truth for the build. Place this file at `docs/PROJECT_SPEC.md`.
> Sections are numbered so prompts can reference them ("implement spec §6.5").
> Decisions made after the first draft are folded in below and tagged **(D1)…(D32)** or **(S1)…(S9)**.
> The reasoning for each lives in `docs/DECISIONS.md`.

---

## 1. Overview

A turn-based, choice-driven story RPG. The player finds the **Heartshard**, an ancient crystal, and travels through a village, a forest, a dungeon, and a kingdom. Choices change:
- story flags
- faction reputation
- NPC affinity
- items and quests

These changes unlock or lock later choices and decide the ending.

**Course goals:** demonstrate Java-2 (OOP, generics, collections, exceptions, JDBC, multithreading, file I/O), Data Structures (graphs, BFS/DFS, topological sort, priority queues, hashing), and DBMS (normalized schema, constraints, transactions, triggers, functions, views, indexes).

### Feature scope

| Tier | Features | Rule |
|---|---|---|
| **MVP (must ship)** | Story graph, choices with conditions/effects, inventory + weapon tiers, combat, quests, save/load, endings, quick ending | The demo depends on these |
| **Core+** | Achievements, reputation/affinity, leaderboard view, story validator, Inspector panel, Demo Mode | Build after MVP works |
| **Stretch** | Audio, shop, NPC portraits, extra chapters | Only if time remains |

### Story structure: branch-and-bottleneck
Branches split and then rejoin at bottleneck nodes. Variety comes from accumulated **state** (flags, reputation, affinity, items, quest states), which later choices and the ending resolver read. Never let branches multiply without folding back.

---

## 2. Tech stack and architecture

| Layer | Choice |
|---|---|
| Language | Java 21 (`maven.compiler.release=21`; builds on any JDK ≥ 21 — S9) |
| Build | Maven (wrapper) |
| Web | Javalin 6 |
| DB access | Plain JDBC + PostgreSQL driver (HikariCP optional) |
| JSON | Jackson |
| Tests | JUnit 5 |
| Database | PostgreSQL 16 |
| Frontend | React + Vite (JavaScript), Framer Motion, plain CSS with variables |

```
React (renders GameView, sends intents)
        │  JSON over HTTP
        ▼
Javalin controllers ──► GameEngine / CombatEngine / QuestManager / AchievementManager
                               │                         ▲
                               ▼                         │ EventBus (Observer)
                        In-memory content (StoryGraph, items, quests)  ◄── loaded once at startup
                               │
                        Repositories (JDBC, hand-written SQL)
                               ▼
                          PostgreSQL
```

**Rules**
- Content is loaded from the DB once at startup. The DB is hit only for profiles, save/load, achievements, endings, and the leaderboard.
- The server re-validates every choice. React is never trusted.
- Sessions are stored in `ConcurrentHashMap<String, GameState>` keyed by a session UUID sent in a header (`X-Session`).
- The map is thread-safe but a `GameState` is not: every public `GameEngine` method that reads or changes a state runs inside `synchronized (state) { … }`, so two fast clicks on the same session are handled one after the other **(D16, D22)**.
- In development the Vite dev server proxies `/api` to Javalin on port 7070, so the browser talks to a single origin and no CORS setup is needed **(S5)**.
- **Content is immutable and shared; game objects are mutable and per session (D20).** Everything loaded at startup (`StoryNode`, `Choice`, `Item`s, `EnemyTemplate`, `Quest`, `Ending`, …) is read-only and shared by all sessions. Anything that changes during play (`GameState`, `Inventory`, `CombatState`, `Enemy`, `EventBus`, `AchievementManager`) is created per session or per fight.
- **Controllers only talk to `GameEngine` for gameplay (D21, D22).** `GameController`, `CombatController` and `SaveController` never call `CombatEngine`, `QuestManager` or `SaveRepository` directly; `GameEngine` takes the state's lock and enforces the mode rules.
- **Encapsulation (D30).** `GameState` and `CombatState` have private fields. Mutation goes through intent methods (`setFlag`, `addReputation`, …); other packages read collections through unmodifiable views; raw collection accessors are package-private (`engine` for `GameState`, `combat` for `CombatState`).

---

## 3. File structure

```
shardbound/
├── CLAUDE.md
├── docs/            PROJECT_SPEC.md, DECISIONS.md, PRD.md, TECH_STACK.md, ARCHITECTURE.md,
│                    SYLLABUS_MAPPING.md, ALGORITHMS.md, story-graph.svg,
│                    diagrams/ (er-diagram.md, class-diagram.md — Mermaid; PNG exports in Phase 10)
├── database/
│   ├── 01_schema.sql
│   ├── 02_functions_triggers.sql
│   ├── 03_views_indexes.sql
│   └── 99_reset.sql
├── content/
│   ├── world.json           # factions, locations, npcs, items, weapons, consumables, enemies,
│   │                        # encounters, quests, quest prereqs, achievements, endings
│   ├── ch0_prologue.json
│   ├── ch1_village.json
│   ├── ch2_forest.json
│   ├── ch3_dungeon.json
│   ├── ch4_kingdom.json
│   └── ch5_finale.json      # Phase 1: RESOLVER + 5 resolver ending nodes as stubs (D2)
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/shardbound/
│       │   ├── App.java
│       │   ├── config/      AppConfig, Database
│       │   ├── api/         GameController, CombatController, SaveController,
│       │   │                ProfileController, MetaController, ErrorHandler
│       │   ├── api/dto/     GameView, ChoiceView, HudView, CombatView, ToastView (records)
│       │   ├── engine/      GameEngine, GameState, SessionStore, EndingResolver, Mode,
│       │   │                Ending, Step, Toast (records)
│       │   ├── story/       StoryNode, Choice, NodeType, StoryGraph, StoryValidator
│       │   ├── ds/          Graph<V,E>
│       │   ├── rules/       Condition (sealed) + FlagCondition, RepCondition, AffinityCondition,
│       │   │                ItemCondition, QuestCondition, StatCondition, Op
│       │   │                ConditionGroup (AND within a grp, OR across grps — D4)
│       │   │                Effect + SetFlagEffect, RepEffect, AffinityEffect, GiveItemEffect,
│       │   │                TakeItemEffect, StartQuestEffect, CompleteQuestEffect, FailQuestEffect,
│       │   │                HealEffect, GoldEffect, XpEffect
│       │   │                RuleFactory
│       │   ├── model/       Faction, Npc, Location (record), Stats, Tier
│       │   ├── item/        Item (abstract), Weapon, Consumable, KeyItem, ItemStack, Inventory
│       │   ├── quest/       Quest, QuestStatus, QuestManager
│       │   ├── combat/      Combatant (abstract), PlayerCombatant, Enemy, EnemyTemplate, EncounterSlot (records, D20),
│       │   │                CombatState, Action, CombatOutcome (D21), CombatEngine, DamageCalculator
│       │   │   └── ai/      EnemyAI, AggressiveAI, DefensiveAI, BossAI
│       │   ├── event/       GameEvent, EventType, EventBus, GameListener
│       │   ├── achievement/ Achievement, AchievementRule, AchievementManager
│       │   ├── persistence/ ContentRepository, SaveRepository, PlayerRepository,
│       │   │                MetaRepository, SqlLog, SaveData (record of raw save rows, D22)
│       │   ├── content/     RawContent (raw rows), JsonContentReader, ContentAssembler, GameContent (I1)
│       │   ├── tools/       ContentImporter, ConsoleRunner, GraphExporter
│       │   └── exception/   GameException, InvalidChoiceException, LockedChoiceException,
│       │                    InventoryFullException, SaveFailedException, ContentValidationException,
│       │                    ActionNotAllowedException, SaveNotFoundException (D27)
│       ├── main/resources/  db.properties.example
│       └── test/java/com/shardbound/
│                            GraphTest, StoryValidatorTest, QuestManagerTest, InventoryTest,
│                            DamageCalculatorTest, ConditionTest, QuestStatusOrderTest (D13),
│                            MaxHpFormulaTest (D28),
│                            SaveRepositoryIT (runs with `mvnw verify` on shardbound_test — S6)
└── frontend/
    ├── vite.config.js       # /api proxy to :7070 (S5)
    └── src/
        ├── api/client.js
        ├── state/GameContext.jsx
        ├── screens/    TitleScreen, ProfileScreen, GameScreen, CombatScreen,
        │               EndingScreen, AchievementsScreen, LeaderboardScreen
        ├── components/ Typewriter, StoryPanel, ChoiceList, Hud, StatBar, InventoryPanel,
        │               WeaponCard, QuestLog, Journal, ReputationPanel, ToastLayer,
        │               SaveLoadModal, InspectorPanel, DamageNumber
        └── styles/     theme.css, tiers.css, regions.css, animations.css
```

---

## 4. Database

### 4.1 Schema — `database/01_schema.sql`

```sql
-- ===== TYPES =====
CREATE TYPE weapon_tier  AS ENUM ('F','E','D','C','B','A','S','SS','SSS');  -- order matters
CREATE TYPE node_type    AS ENUM ('STORY','COMBAT','RESOLVER','ENDING');
CREATE TYPE item_type    AS ENUM ('WEAPON','CONSUMABLE','KEY');
CREATE TYPE quest_status AS ENUM ('LOCKED','AVAILABLE','ACTIVE','COMPLETED','FAILED');

-- ===== STATIC CONTENT =====
CREATE TABLE faction  (faction_id VARCHAR(20) PRIMARY KEY, name VARCHAR(60) NOT NULL);

CREATE TABLE location (
  location_id VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  region      VARCHAR(10) NOT NULL CHECK (region IN ('VILLAGE','FOREST','DUNGEON','KINGDOM','SPIRE'))  -- D19
);

CREATE TABLE npc (
  npc_id      VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(60) NOT NULL,
  faction_id  VARCHAR(20) REFERENCES faction,
  location_id VARCHAR(40) REFERENCES location
);

CREATE TABLE item (
  item_id     VARCHAR(40) PRIMARY KEY,
  name        VARCHAR(80) NOT NULL,
  type        item_type   NOT NULL,
  description TEXT        NOT NULL,
  price       INT         NOT NULL DEFAULT 0 CHECK (price >= 0)
);
-- Generalization/specialization (ISA)
CREATE TABLE weapon (
  item_id  VARCHAR(40) PRIMARY KEY REFERENCES item ON DELETE CASCADE,
  tier     weapon_tier NOT NULL,
  attack   INT NOT NULL CHECK (attack > 0),
  crit_pct INT NOT NULL CHECK (crit_pct BETWEEN 0 AND 100),
  ability  VARCHAR(80)
);
CREATE TABLE consumable (
  item_id VARCHAR(40) PRIMARY KEY REFERENCES item ON DELETE CASCADE,
  heal    INT NOT NULL DEFAULT 0,
  energy  INT NOT NULL DEFAULT 0
);

CREATE TABLE enemy (
  enemy_id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  hp INT NOT NULL CHECK (hp > 0), atk INT NOT NULL, def INT NOT NULL, spd INT NOT NULL,
  xp_reward INT NOT NULL, gold_reward INT NOT NULL,
  ai VARCHAR(12) NOT NULL CHECK (ai IN ('AGGRESSIVE','DEFENSIVE','BOSS'))
);
CREATE TABLE encounter (
  encounter_id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  can_flee BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE encounter_enemy (
  encounter_id VARCHAR(40) REFERENCES encounter ON DELETE CASCADE,
  enemy_id     VARCHAR(40) REFERENCES enemy,
  qty          SMALLINT NOT NULL CHECK (qty BETWEEN 1 AND 4),
  PRIMARY KEY (encounter_id, enemy_id)
);

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
  flee_node    VARCHAR(40) REFERENCES story_node DEFERRABLE INITIALLY DEFERRED,  -- D9: optional; NULL = flee goes to lose_node
  CHECK (type <> 'COMBAT' OR (encounter_id IS NOT NULL AND win_node IS NOT NULL AND lose_node IS NOT NULL)),
  CHECK (flee_node IS NULL OR type = 'COMBAT')                                    -- D9, D31: only COMBAT nodes may flee
);

-- choice_id stays stable across re-imports because the importer upserts on
-- the natural key UNIQUE (from_node, sort_order) instead of deleting (D1).
CREATE TABLE choice (
  choice_id  SERIAL PRIMARY KEY,
  from_node  VARCHAR(40) NOT NULL REFERENCES story_node ON DELETE CASCADE,
  to_node    VARCHAR(40) NOT NULL REFERENCES story_node DEFERRABLE INITIALLY DEFERRED,
  label      VARCHAR(160) NOT NULL,
  sort_order SMALLINT NOT NULL,
  hidden_when_locked BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (from_node, sort_order)
);

-- grp (D4): conditions with the same grp are ANDed; different grps are ORed.
-- A choice with no condition rows is always available.
CREATE TABLE choice_condition (
  choice_id INT REFERENCES choice ON DELETE CASCADE,
  seq       SMALLINT,
  grp       SMALLINT NOT NULL DEFAULT 0,
  cond_type VARCHAR(10) NOT NULL CHECK (cond_type IN ('FLAG','REP','AFFINITY','ITEM','QUEST','STAT')),
  key       VARCHAR(40) NOT NULL,
  op        VARCHAR(2)  NOT NULL CHECK (op IN ('=','!=','>','>=','<','<=')),
  value     INT NOT NULL,
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
  reward_xp   INT NOT NULL DEFAULT 0,
  reward_gold INT NOT NULL DEFAULT 0,
  is_main     BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE quest_prereq (
  quest_id    VARCHAR(40) REFERENCES quest ON DELETE CASCADE,
  requires_id VARCHAR(40) REFERENCES quest ON DELETE CASCADE,
  PRIMARY KEY (quest_id, requires_id),
  CHECK (quest_id <> requires_id)
);

CREATE TABLE achievement (
  achievement_id VARCHAR(40) PRIMARY KEY,
  title VARCHAR(80) NOT NULL,
  description TEXT NOT NULL,
  hidden BOOLEAN NOT NULL DEFAULT FALSE
);

-- Convention: an ending's node_id equals its ending_id (e.g. node 'END_QUICK').
CREATE TABLE ending (
  ending_id    VARCHAR(40) PRIMARY KEY,
  node_id      VARCHAR(40) NOT NULL UNIQUE REFERENCES story_node,
  title        VARCHAR(80) NOT NULL,
  priority     SMALLINT NOT NULL UNIQUE,         -- lower = checked first
  via_resolver BOOLEAN  NOT NULL DEFAULT TRUE    -- D11: FALSE = reached directly (END_QUICK), skipped by EndingResolver
);
CREATE TABLE ending_condition (
  ending_id VARCHAR(40) REFERENCES ending ON DELETE CASCADE,
  seq SMALLINT,
  grp SMALLINT NOT NULL DEFAULT 0,              -- D4: same grp = AND, different grp = OR
  cond_type VARCHAR(10) NOT NULL CHECK (cond_type IN ('FLAG','REP','AFFINITY','ITEM','QUEST','STAT')),
  key VARCHAR(40) NOT NULL,
  op VARCHAR(2) NOT NULL CHECK (op IN ('=','!=','>','>=','<','<=')),
  value INT NOT NULL,
  PRIMARY KEY (ending_id, seq)
);

-- ===== DYNAMIC PLAYER STATE =====
-- Every FK from a player table to a content table is ON DELETE RESTRICT (written
-- out explicitly), except save_history.choice_id, which is ON DELETE SET NULL.
-- This lets ContentImporter remove content that is gone from the JSON while
-- refusing to remove anything a save still points at (D1).
CREATE TABLE player (
  player_id  SERIAL PRIMARY KEY,
  username   VARCHAR(30) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE save_slot (
  save_id      SERIAL PRIMARY KEY,
  player_id    INT NOT NULL REFERENCES player ON DELETE CASCADE,
  slot_no      SMALLINT NOT NULL CHECK (slot_no BETWEEN 0 AND 3),   -- 0 = autosave
  current_node VARCHAR(40) NOT NULL REFERENCES story_node ON DELETE RESTRICT,
  -- max_hp/atk/def/spd/max_energy are NOT stored: Java derives them from level (D6, D28).
  hp INT NOT NULL,
  energy INT NOT NULL CHECK (energy >= 0),    -- D32
  level INT NOT NULL CHECK (level >= 1),
  xp INT NOT NULL,                   -- progress within the current level, reset on level-up (D29)
  gold INT NOT NULL CHECK (gold >= 0),
  rng_seed BIGINT NOT NULL,          -- the fresh seed drawn at save time (D7)
  demo_mode BOOLEAN NOT NULL DEFAULT FALSE,   -- D25: restored on load
  play_seconds INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (player_id, slot_no),
  -- D28: same formula as Stats.maxHp(); MaxHpFormulaTest keeps the two in sync
  CHECK (hp BETWEEN 0 AND 40 + 5 * (level - 1))
);

CREATE TABLE save_inventory (
  save_id  INT REFERENCES save_slot ON DELETE CASCADE,
  item_id  VARCHAR(40) REFERENCES item ON DELETE RESTRICT,
  qty      INT NOT NULL CHECK (qty > 0),
  equipped BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (save_id, item_id)
);
-- Flags whose key starts with '_' are reserved for the engine (e.g. _locked_passed, D15).
CREATE TABLE save_flag (
  save_id INT REFERENCES save_slot ON DELETE CASCADE,
  flag_key VARCHAR(40), value INT NOT NULL,
  PRIMARY KEY (save_id, flag_key)
);
CREATE TABLE save_reputation (
  save_id INT REFERENCES save_slot ON DELETE CASCADE,
  faction_id VARCHAR(20) REFERENCES faction ON DELETE RESTRICT,
  value INT NOT NULL CHECK (value BETWEEN -100 AND 100),
  PRIMARY KEY (save_id, faction_id)
);
CREATE TABLE save_affinity (
  save_id INT REFERENCES save_slot ON DELETE CASCADE,
  npc_id VARCHAR(40) REFERENCES npc ON DELETE RESTRICT,
  value INT NOT NULL CHECK (value BETWEEN -100 AND 100),
  PRIMARY KEY (save_id, npc_id)
);
CREATE TABLE save_quest (
  save_id INT REFERENCES save_slot ON DELETE CASCADE,
  quest_id VARCHAR(40) REFERENCES quest ON DELETE RESTRICT,
  status quest_status NOT NULL,
  PRIMARY KEY (save_id, quest_id)
);
-- step_no 1 = oldest step. GameState.history is a stack (newest first), so
-- insertHistory walks it with descendingIterator() (S7).
CREATE TABLE save_history (
  save_id INT REFERENCES save_slot ON DELETE CASCADE,
  step_no INT,
  node_id VARCHAR(40) REFERENCES story_node ON DELETE RESTRICT,
  choice_id INT REFERENCES choice ON DELETE SET NULL,   -- D1: a removed choice only blanks the journal entry
  PRIMARY KEY (save_id, step_no)
);

-- Profile-level: survive save deletion
CREATE TABLE player_achievement (
  player_id INT REFERENCES player ON DELETE CASCADE,
  achievement_id VARCHAR(40) REFERENCES achievement ON DELETE RESTRICT,
  unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (player_id, achievement_id)
);
CREATE TABLE player_ending (
  player_id INT REFERENCES player ON DELETE CASCADE,
  ending_id VARCHAR(40) REFERENCES ending ON DELETE RESTRICT,
  first_reached TIMESTAMPTZ NOT NULL DEFAULT now(),
  best_seconds INT NOT NULL,
  PRIMARY KEY (player_id, ending_id)
);

CREATE TABLE save_audit (
  audit_id SERIAL PRIMARY KEY,
  save_id INT,
  action VARCHAR(10),
  node_id VARCHAR(40),
  at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 4.2 Functions and triggers — `database/02_functions_triggers.sql`

```sql
CREATE OR REPLACE FUNCTION trg_touch_save() RETURNS trigger AS $$
BEGIN NEW.updated_at := now(); RETURN NEW; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER touch_save BEFORE UPDATE ON save_slot
FOR EACH ROW EXECUTE FUNCTION trg_touch_save();

CREATE OR REPLACE FUNCTION trg_audit_save() RETURNS trigger AS $$
BEGIN
  INSERT INTO save_audit(save_id, action, node_id) VALUES (NEW.save_id, TG_OP, NEW.current_node);
  RETURN NEW;
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER audit_save AFTER INSERT OR UPDATE ON save_slot
FOR EACH ROW EXECUTE FUNCTION trg_audit_save();

-- Meta-achievement owned by the DB
CREATE OR REPLACE FUNCTION trg_all_endings() RETURNS trigger AS $$
BEGIN
  IF (SELECT COUNT(*) FROM player_ending WHERE player_id = NEW.player_id)
     = (SELECT COUNT(*) FROM ending) THEN
    INSERT INTO player_achievement(player_id, achievement_id)
    VALUES (NEW.player_id, 'ALL_ENDINGS') ON CONFLICT DO NOTHING;
  END IF;
  RETURN NEW;
END; $$ LANGUAGE plpgsql;
CREATE TRIGGER all_endings AFTER INSERT ON player_ending
FOR EACH ROW EXECUTE FUNCTION trg_all_endings();

-- Uses ENUM ordering: SSS > SS > S > A ...
CREATE OR REPLACE FUNCTION fn_best_weapon(p_save INT)
RETURNS TABLE(name TEXT, tier weapon_tier, attack INT) AS $$
  SELECT i.name::text, w.tier, w.attack
  FROM save_inventory si
  JOIN weapon w ON w.item_id = si.item_id
  JOIN item   i ON i.item_id = w.item_id
  WHERE si.save_id = p_save
  ORDER BY w.tier DESC, w.attack DESC
  LIMIT 1;
$$ LANGUAGE sql STABLE;
```

### 4.3 Views and indexes — `database/03_views_indexes.sql`

```sql
CREATE VIEW v_leaderboard AS
SELECT p.username,
       COUNT(DISTINCT pe.ending_id)      AS endings,
       COUNT(DISTINCT pa.achievement_id) AS achievements,
       MIN(pe.best_seconds)              AS fastest_ending_s
FROM player p
LEFT JOIN player_ending      pe ON pe.player_id = p.player_id
LEFT JOIN player_achievement pa ON pa.player_id = p.player_id
GROUP BY p.player_id, p.username;
-- COUNT(DISTINCT) because two LEFT JOINs multiply rows.

CREATE UNIQUE INDEX one_equipped_per_save ON save_inventory(save_id) WHERE equipped;
-- Postgres does not auto-index FK columns
CREATE INDEX idx_choice_from  ON choice(from_node);
CREATE INDEX idx_history_save ON save_history(save_id);
```

### 4.4 Save transaction (Java)

```java
public void save(GameState s, int playerId, int slot) {
    try (Connection c = db.getConnection()) {
        c.setAutoCommit(false);
        try {
            int saveId = upsertSlot(c, s, playerId, slot);   // INSERT ... ON CONFLICT ... RETURNING save_id
            deleteChildren(c, saveId);
            insertInventory(c, saveId, s.inventory());       // addBatch / executeBatch
            insertFlags(c, saveId, s.flagsView());           // unmodifiable views (D30)
            insertReputation(c, saveId, s.reputationView());
            insertAffinity(c, saveId, s.affinityView());
            insertQuests(c, saveId, s.questsView());
            insertHistory(c, saveId, s.historyOldestFirst());
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw new SaveFailedException("Save rolled back; previous save intact", e);
        }
    } catch (SQLException e) {
        throw new SaveFailedException("DB unavailable", e);
    }
}
```

```sql
INSERT INTO save_slot (player_id, slot_no, current_node, hp, energy, level, xp, gold, rng_seed, demo_mode, play_seconds)
VALUES (?,?,?,?,?,?,?,?,?,?,?)
ON CONFLICT (player_id, slot_no) DO UPDATE SET
  current_node = EXCLUDED.current_node, hp = EXCLUDED.hp,
  energy = EXCLUDED.energy, level = EXCLUDED.level, xp = EXCLUDED.xp, gold = EXCLUDED.gold,
  rng_seed = EXCLUDED.rng_seed, demo_mode = EXCLUDED.demo_mode, play_seconds = EXCLUDED.play_seconds,
  version = save_slot.version + 1
RETURNING save_id;
```

**Crash simulation (demo feature):** if `AppConfig.simulateSaveCrash` is true, throw an exception after `insertInventory`. This lets the demo show that rollback keeps the previous save intact.

**Who calls `save` (D22).** Only `GameEngine`. `SaveController` calls `GameEngine.save(s, slot)` and `GameEngine.load(playerId, slot)`; it never touches `SaveRepository`. `GameEngine.save`:
```java
public void save(GameState s, int slot) {
    synchronized (s) {
        if (s.mode() != Mode.STORY)
            throw new ActionNotAllowedException("Cannot save during combat or after the ending");  // 409 (D27)
        s.reseedForSave();                          // D7, on the live state
        saves.save(s, s.playerId(), slot);          // student-implemented transaction
    }
}
```

**RNG reseed before saving (D7).** `GameState.reseedForSave()` does:
```java
long fresh = rng.nextLong();   // continue the current sequence…
rngSeed = fresh;               // …and remember where it now starts
rng = new Random(fresh);
```
The fresh seed is what `save_slot.rng_seed` stores, so "save then keep playing" and "save, load, keep playing" produce identical rolls.

**Load returns raw rows (D22).** `SaveRepository.load(playerId, slot)` returns a `SaveData` record (slot columns plus lists/maps of the child rows, ids only) or throws `SaveNotFoundException` (404, D27). `GameEngine.load` turns it into a `GameState` using the in-memory content catalog (items, quests), so the repository needs no content objects. See §6.10 for the rest of the rebuild.

**History order (S7).** `GameState` stores history as a stack (newest on top). Its public `historyOldestFirst()` returns a copy in oldest-first order, so `step_no` 1 is the oldest step; load pushes the rows back in `step_no` order.

**Integration test (S6).** `SaveRepositoryIT` runs against a separate `shardbound_test` database (`test.db.*` keys in `db.properties`) through the failsafe plugin: `.\mvnw.cmd verify`.

### 4.5 Responsibility split
- **Java:** event-driven gameplay achievements.
- **DB triggers:** aggregate facts about stored data (`ALL_ENDINGS`), audit, and timestamps.

### 4.6 JDBC conventions
- Every query is a `PreparedStatement` with `?` placeholders; no string concatenation of values.
- PostgreSQL enum columns get an explicit cast in the SQL text, because the driver sends Java strings as `varchar` **(D5)**:
  ```sql
  INSERT INTO weapon (item_id, tier, attack, crit_pct, ability) VALUES (?, ?::weapon_tier, ?, ?, ?);
  INSERT INTO save_quest (save_id, quest_id, status) VALUES (?, ?, ?::quest_status);
  ```
- Multi-row writes use `addBatch()` / `executeBatch()` inside one transaction (CLAUDE.md rule 3).

---

## 5. Data structures (use exactly these)

| Where | Structure | Why | Complexity |
|---|---|---|---|
| Story nodes | `HashMap<String, StoryNode>` | O(1) lookup by id | O(1) |
| Story edges | Adjacency list `HashMap<String, List<Choice>>` | Sparse graph | O(k) per node |
| Structural edges (D10) | `StoryGraph` builds a `Graph<String, String>` (edge value = label such as `"choice 12"`, `"win"`, `"flee"`, `"resolver"`) from choices **plus** COMBAT → win/lose/flee and RESOLVER → every `via_resolver` ending node | Validator sees every real exit; `StoryValidator` uses the student's `Graph.bfs/dfs/shortestPath` | O(V+E) build |
| Choice / ending conditions | `ConditionGroup`: `TreeMap<Integer, List<Condition>>` keyed by grp (D4) | AND within a group, OR across groups, stable describe() order | O(c) test |
| Validator / quick route | BFS with `ArrayDeque` + parent map | Reachability, shortest path | O(V+E) |
| Dead ends / cycles | Iterative DFS with a stack | Detect nodes with no exit | O(V+E) |
| Generic graph | Own `Graph<V,E>` in `ds/` | Generics + DS demo | — |
| Inventory | `LinkedHashMap<String, ItemStack>`, capacity 20 | O(1) add/remove, stable order | O(1); sort O(n log n) |
| Weapon tiers | `enum Tier` + `Comparator` | Same order as Postgres enum | O(1) |
| Flags | `HashMap<String, Integer>` | Sparse, open keys | O(1) |
| Reputation | `EnumMap<Faction, Integer>` | Fixed keys, array-backed | O(1) |
| Affinity | `HashMap<String, Integer>` | Per NPC | O(1) |
| Quest dependencies | DAG + Kahn's topological sort | Order + cycle detection | O(V+E) |
| Quest states | `HashMap<String, QuestStatus>` | O(1) status | O(1) |
| Combat turn order | `PriorityQueue<Combatant>` by speed desc | Fastest acts first | O(log n) |
| Enemy content | `HashMap<String, EnemyTemplate>` of immutable records; `CombatEngine.start` builds fresh `Enemy` objects per fight (D20) | Shared content can never be mutated by one session | O(1) lookup; O(k) per fight |
| Combat log | Bounded `ArrayDeque<String>` (last 8) | O(1) both ends | O(1) |
| Journal / history | `ArrayDeque<Step>` as stack | Push steps, pop for previous page | O(1) |
| Achievement rules | `static final EnumMap<EventType, List<AchievementRule>>`, shared by all sessions (D23) | Check only relevant rules | O(rules per event) |
| Unlocked achievements | `HashSet<String>`, one per session, seeded from the player's DB rows (D23) | O(1) contains | O(1) |
| SQL log | Bounded `ArrayDeque<String>` (last 5) behind `synchronized` methods (D26) | Shared by all request threads | O(1) |
| Endings | `List<Ending>` (only `via_resolver = true`) sorted by priority | First match wins | O(e·c) |
| Pending toasts | `ArrayDeque<Toast>` as FIFO queue | Show in order | O(1) |
| Sessions | `ConcurrentHashMap<String, GameState>` | Thread-safe | O(1) |

### Java-2 coverage checklist

| Concept | Where |
|---|---|
| Inheritance / abstract | `Item` → `Weapon`/`Consumable`/`KeyItem`; `Combatant` → `PlayerCombatant`/`Enemy` |
| Interfaces / polymorphism | `Condition`, `Effect`, `EnemyAI`, `GameListener` |
| Sealed interfaces, records, switch expressions | `Condition`, DTOs, `RuleFactory` |
| Generics | `Graph<V,E>`, `ItemStack` |
| Enums with fields | `Tier`, `QuestStatus`, `EventType`, `Faction` |
| Custom exceptions | `exception/`, mapped to HTTP codes in `ErrorHandler` |
| Collections + streams | throughout |
| Encapsulation | private fields + intent methods + unmodifiable views in `GameState`, `CombatState` (D30) |
| Multithreading | autosave on single-thread `ExecutorService` using a deep-copied snapshot (D24); `ConcurrentHashMap` sessions; per-state `synchronized` request handling (D16); `synchronized` `SqlLog` (D26); immutable shared content (D20) |
| JDBC | repositories, batch inserts, transactions |
| File I/O | `ContentImporter` (JSON in), `GraphExporter` (DOT out) |
| Patterns | Observer, Strategy, Factory, Repository, State machine |

---

## 6. Game logic

### 6.1 GameState

```java
public class GameState {
    private String sessionId; private int playerId;
    private String currentNode; private Mode mode;           // STORY, COMBAT, ENDED
    private Stats stats;                                     // hp, energy, level, xp, gold (+ derived stats)
    private Inventory inventory;
    private final Map<String,Integer> flags = new HashMap<>();
    private final EnumMap<Faction,Integer> reputation = new EnumMap<>(Faction.class);
    private final Map<String,Integer> affinity = new HashMap<>();
    private final Map<String,QuestStatus> quests = new HashMap<>();
    private final Deque<Step> history = new ArrayDeque<>();
    private CombatState combat;                              // null outside combat
    private long rngSeed; private Random rng;
    private long sessionStartMillis; private int playSecondsBefore;
    private final Queue<Toast> pendingToasts = new ArrayDeque<>();
    private boolean demoMode;
    private EventBus bus;                                    // one per session; knows its GameState

    // ---- public API used by rules, combat, persistence and views (D30) ----
    public int flag(String key);                             // missing -> 0
    public void setFlag(String key, int value);
    public int reputation(Faction f);                        // missing -> 0
    public void addReputation(Faction f, int delta);         // clamps -100..100
    public int affinity(String npcId);
    public void addAffinity(String npcId, int delta);        // clamps -100..100
    public QuestStatus questStatus(String questId);
    public void setQuestStatus(String questId, QuestStatus st); // called only by QuestManager
    public void addToast(Toast t);
    public Map<String,Integer> flagsView();                  // unmodifiable view
    public Map<Faction,Integer> reputationView();            // unmodifiable view
    public Map<String,Integer> affinityView();               // unmodifiable view
    public Map<String,QuestStatus> questsView();             // unmodifiable view
    public List<Step> historyOldestFirst();                  // new list, O(n)
    public long reseedForSave();                             // D7
    public GameState deepCopy();                             // D24
    // getters for sessionId, playerId, currentNode, mode, stats, inventory, combat, rng, demoMode, bus

    // ---- package-private (engine only): raw collections for GameEngine ----
    Deque<Step> historyStack();                              // push on choose, rebuild on load
    Queue<Toast> toastQueue();                               // drained into each GameView
}
```

No package outside `engine` ever receives a raw mutable collection from `GameState` (D30).

Starting stats: hp 40, atk 6, def 2, spd 5, energy 3, gold 15, level 1, weapon `rusty_shortsword` (F) equipped.

**Derived stats (D6, D28).** Only `hp`, `energy`, `level`, `xp` and `gold` are stored. Everything else is computed from `level` by `Stats`:

| Stat | Formula |
|---|---|
| maxHp | `40 + 5 * (level − 1)` — the same expression is in the `save_slot` CHECK; `MaxHpFormulaTest` asserts they match |
| atk | `6 + (level − 1)` |
| def | `2` |
| spd | `5` |
| maxEnergy | `3` |

**XP is progress within the current level (D29).** `xpToNext = 100 * level`. When `xp ≥ xpToNext`, the player levels up and `xp` resets to the leftover amount (`xp − xpToNext`); this repeats if enough XP was gained for several levels. Because `xp` is not a running total, `level` cannot be computed from `xp`, so storing both does not create a functional dependency between them.

**Reserved flags (D15).** Flag keys that start with `_` belong to the engine, not to content. `_locked_passed` counts choices taken that had a REP or AFFINITY condition (for SILVER_TONGUE). Content may read them but may never `SET_FLAG` them (validator error). Because they are ordinary flags, they are saved in `save_flag` with no extra table.

### 6.2 Conditions and effects

```java
public sealed interface Condition
    permits FlagCondition, RepCondition, AffinityCondition, ItemCondition, QuestCondition, StatCondition {
    boolean test(GameState s);
    String describe();   // "Requires Eldmere reputation ≥ 20" — shown on locked choices
}

public interface Effect {
    void apply(GameState s, EventBus bus);   // publishes events → toasts + achievements
}

/** D4: one class evaluates both choice conditions and ending conditions. */
public final class ConditionGroup {
    // grp -> conditions in that group; same grp = AND, different grps = OR
    private final TreeMap<Integer, List<Condition>> groups;
    public boolean test(GameState s);   // true if empty, or if ANY group has ALL conditions true
    public String describe();          // "Requires A and B, or C"
}
```

**Condition semantics**

| Type | Meaning |
|---|---|
| FLAG | Compare a flag's value; a missing flag counts as 0 |
| REP | Compare faction reputation; missing counts as 0 |
| AFFINITY | Compare NPC affinity; missing counts as 0 |
| ITEM | Compare quantity owned |
| QUEST | op `=` or `!=`. JSON writes the status **name** (`"value": "COMPLETED"`); `ContentImporter` converts it to the `QuestStatus` ordinal stored in the INT `value` column (D13). `QuestStatusOrderTest` asserts that Java's `QuestStatus.values()` order equals the `quest_status` enum order in `01_schema.sql` |
| STAT | key in `level`, `gold`, `hp` |

**Effect semantics**

| Type | Meaning |
|---|---|
| SET_FLAG | `flags[key] = value` |
| REP / AFFINITY | Add `value`, clamp to −100…100 |
| GIVE_ITEM / TAKE_ITEM | Add/remove `value` quantity |
| START_QUEST / COMPLETE_QUEST / FAIL_QUEST | Quest transitions; completion grants rewards |
| HEAL | +value hp, clamp to maxHp |
| GOLD | Add value, never below 0 |
| XP | Add value, then level up while `xp ≥ xpToNext` (`xpToNext = 100 * level`; xp resets to the leftover; maxHp and atk follow the §6.1 formulas) (D29) |

Every effect publishes an event, which produces a toast (e.g. "+5 Eldmere reputation", "Obtained: Iron Longsword").

### 6.3 Choose flow

```java
// Every public GameEngine method locks the state itself (D16, D22).
public GameView choose(GameState s, int choiceId) {
    synchronized (s) {
        if (s.mode() != Mode.STORY)
            throw new ActionNotAllowedException("Choices are not available right now");   // 409 (D27)
        EventBus bus = s.bus();
        Choice c = graph.choice(s.currentNode(), choiceId)
            .orElseThrow(() -> new InvalidChoiceException(choiceId));
        if (!c.conditions().test(s))                       // ConditionGroup (D4)
            throw new LockedChoiceException(c);
        if (c.hasRepOrAffinityCondition()) {               // D15: SILVER_TONGUE counter
            s.setFlag("_locked_passed", s.flag("_locked_passed") + 1);
            bus.publish(new GameEvent(EventType.LOCKED_CHOICE_PASSED, Map.of("choice", choiceId)));
        }
        c.effects().forEach(e -> e.apply(s, bus));
        s.historyStack().push(new Step(s.currentNode(), choiceId));
        return enterNode(s, c.to());
    }
}

private GameView enterNode(GameState s, String nodeId) {
    StoryNode previous = graph.node(s.currentNode());
    StoryNode n = graph.node(nodeId);
    s.setCurrentNode(nodeId);
    s.bus().publish(new GameEvent(EventType.NODE_ENTERED, Map.of("node", nodeId)));
    if (n.chapter() != previous.chapter()) autosave(s);    // D17, before the type switch
    return switch (n.type()) {
        case STORY    -> views.story(s, n);
        case COMBAT   -> { s.setCombat(combatEngine.start(s, n.encounterId())); s.setMode(Mode.COMBAT); yield views.combat(s); }
        case RESOLVER -> enterNode(s, endingResolver.resolve(s).nodeId());
        case ENDING   -> finish(s, n);
    };
}

// D24: runs on the request thread while the caller already holds synchronized (s).
private void autosave(GameState s) {
    s.reseedForSave();                       // 1. reseed the LIVE state (D7)
    GameState snapshot = s.deepCopy();       // 2. copy without bus, combat, pendingToasts
    autosaver.submit(() -> saves.save(snapshot, snapshot.playerId(), 0));   // 3. background write
}
```

Locked choices are returned with `locked: true` and `lockReason` (from `ConditionGroup.describe()`), unless `hidden_when_locked` is set.

**No on-enter effects (D8).** Entering a node never changes state by itself; only choice effects do. Anything a scene "gives" is an effect on a choice in that scene (e.g. p02's "Take the crystal" choice carries `GIVE_ITEM heartshard`). Combat XP and gold come from the defeated enemies' `xp_reward` / `gold_reward`; item loot is an effect on a choice in the `win_node`.

**Autosave trigger (D17).** `enterNode` autosaves to slot 0 whenever the new node's `chapter` differs from the previous node's `chapter`. The autosave happens before the node's type is handled, so the state is always in STORY mode when it is copied.

**Deep copy rules (D24).** `deepCopy()` copies every saved field (stats, inventory stacks, flags, reputation, affinity, quests, history, rng seed, demo mode, play time) into new collections. It deliberately leaves out `bus` (listeners belong to the live session), `combat` (saving is STORY-only) and `pendingToasts` (UI-only). The copy is never shared back with the live state.

### 6.4 Inventory and weapon tiers

```java
public enum Tier {
    F(1.00), E(1.10), D(1.20), C(1.35), B(1.50), A(1.70), S(2.00), SS(2.40), SSS(3.00);
    public final double multiplier;
    Tier(double m) { this.multiplier = m; }
}
```

- Capacity: 20 slots. Consumables stack; weapons and key items do not.
- Only one weapon can be equipped at a time.
- `weaponsByTier()` sorts by tier descending, then attack descending.

**Weapon ladder (deterministic quest rewards only, never random drops)**

| Tier | id | Name | Atk | Crit% | Source |
|---|---|---|---|---|---|
| F | rusty_shortsword | Rusty Shortsword | 3 | 5 | Starting gear |
| E | woodcutter_hatchet | Woodcutter's Hatchet | 5 | 5 | Ch1 woodcutter quest; also quick-route loot |
| D | hunter_longbow | Hunter's Longbow | 6 | 10 | Ch1 wolf hunt |
| C | iron_longsword | Iron Longsword | 8 | 8 | Ch1 blacksmith errand |
| B | thornwood_spear | Thornwood Spear | 10 | 10 | Ch2 Warden trial |
| A | moonsteel_blade | Moonsteel Blade | 13 | 12 | Ch3 side vault |
| S | kingsguard_halberd | Kingsguard Halberd | 16 | 12 | Ch4 royal tournament |
| SS | dragonbone_greatsword | Dragonbone Greatsword | 20 | 15 | Ch3 hidden boss (requires the A-tier quest + a flag) |
| SSS | aetherion | Aetherion, the Crystal Edge | 26 | 20 | Secret chain: all 3 Shard quests + no faction below 0 |

### 6.5 Quests
- **State machine:** `LOCKED → AVAILABLE → ACTIVE → COMPLETED | FAILED`.
- **Initial states (D14):** on a new game, quests with no prerequisites start `AVAILABLE`; all others start `LOCKED`.
- **LOCKED → AVAILABLE:** when all prerequisites are `COMPLETED` (re-evaluated after every quest completion).
- **Valid effect transitions (D14):** `START_QUEST` AVAILABLE → ACTIVE; `COMPLETE_QUEST` ACTIVE → COMPLETED; `FAIL_QUEST` ACTIVE → FAILED.
- **Invalid transitions never throw during play (D14).** `QuestManager` logs a warning (SLF4J) and leaves the quest unchanged. `StoryValidator` catches the cases it can find statically (see §8.4).
- Objectives are story-driven: effects in choices start, complete, or fail quests.
- `QuestManager.topoOrder()` uses Kahn's algorithm. It runs at startup and throws `ContentValidationException` on a cycle; the quest log displays quests in this order.
- **Main quests:** `q_strange_light` (prologue), `q_shard_one` (Ch2), `q_shard_two` (Ch3), `q_shard_three` (Ch4), `q_crystal_edge` (requires all three shard quests).

### 6.6 Combat
- **Fresh enemies per fight (D20).** `ContentRepository.loadEnemies()` returns immutable `EnemyTemplate` records (id, name, hp, atk, def, spd, xpReward, goldReward, ai). `CombatEngine.start(s, encounterId)` builds new `Enemy` objects from the templates for that fight only (applying Demo Mode ×0.6 to hp/atk), each with its own `EnemyAI` instance. No mutable combat object is ever shared between sessions.
- **Actions:** `ATTACK`, `DEFEND` (doubles def until the next own turn), `SKILL` (Crystal Pulse: ×1.8 applied to `base`, before the random roll and crit; costs 1 energy) **(S1)**, `ITEM`, `FLEE` (chance = 50% + (playerSpd − avgEnemySpd) × 5%, clamp 10–90%; disabled if `can_flee` is false).
- **Energy (S2):** refilled to `maxEnergy` at the start of every combat. Consumables with `energy > 0` also restore it.
- **Round:** load living combatants into a `PriorityQueue` by speed desc → poll each → player uses the queued action, enemies use `EnemyAI.decide()` → log → check win/lose.
- **Speed ties (S1):** the player acts first; among enemies, the lower encounter index acts first.
- **The API is turn-based:** one POST = the player's action plus all enemy actions until the player's next turn.
- **Who routes the result (D21).** `CombatEngine.act(s, action)` only runs the round and returns a `CombatOutcome` (`ONGOING`, `WON`, `LOST`, `FLED`). `GameEngine.combatAction(s, action)` takes the lock, checks the action is allowed (`ActionNotAllowedException` → 409 if not in combat, FLEE when `can_flee` is false, SKILL with 0 energy, or an unusable ITEM), calls `act`, then routes:
  ```java
  public GameView combatAction(GameState s, Action a) {
      synchronized (s) {
          requireCombat(s);                                    // 409 otherwise
          CombatOutcome out = combatEngine.act(s, a);
          return switch (out) {
              case ONGOING -> views.combat(s);
              case WON     -> leaveCombat(s, node().winNode());   // XP, gold already applied by CombatEngine
              case LOST    -> leaveCombat(s, node().loseNode());  // penalty already applied
              case FLED    -> leaveCombat(s, node().fleeNode() != null ? node().fleeNode() : node().loseNode());
          };
      }
  }
  ```
  `leaveCombat` clears `combat`, sets mode back to STORY and calls `enterNode`. `CombatController` calls only `GameEngine`.

**Damage formula (`DamageCalculator`, unit-tested):**
```
base = attacker.atk * weapon.tier.multiplier + weapon.attack     (enemies: atk only, multiplier 1)
roll = base * (0.9 + rng.nextDouble() * 0.2)
crit = rng.nextInt(100) < critPct → ×1.5
dmg  = max(1, round(roll * crit) - defender.def * (defending ? 2 : 1))
```

**AI**
- `AggressiveAI`: always attacks.
- `DefensiveAI`: defends at < 30% HP once, otherwise attacks.
- `BossAI`: phase 2 at ≤ 50% HP; a heavy attack (×1.6) every 3rd turn.

**Outcomes**
- Win → XP and gold from the defeated enemies' `xp_reward` / `gold_reward` → `win_node`. Item loot is a choice effect in the win node (D8).
- Loss → `lose_node` (lose 30% gold, hp set to 50% of maxHp).
- Flee succeeds → `flee_node` if set, otherwise `lose_node`. **No penalty either way** (D9).
- Boss loss → Java sets `offerReload: true` in the `GameView`; the UI shows a "Reload last save" button that calls the normal load endpoint (S3).

**RNG and Demo Mode:** seeded `Random(rngSeed)`, persisted with the save. Demo Mode multiplies enemy hp/atk by 0.6.

### 6.7 Reputation and affinity
- Factions: `ELDMERE`, `SYLVAN`, `CROWN`, `ASHEN`.
- Reputation and affinity are only written by effects and only read by conditions and endings.

### 6.8 Achievements (Observer)

```java
public record GameEvent(EventType type, Map<String,Object> data) {}
```

`EventType` values: `NODE_ENTERED`, `ITEM_GAINED`, `ITEM_EQUIPPED`, `REP_CHANGED`, `AFFINITY_CHANGED`, `QUEST_STARTED`, `QUEST_COMPLETED`, `QUEST_FAILED`, `COMBAT_WON`, `COMBAT_LOST`, `LOCKED_CHOICE_PASSED`, `ENDING_REACHED`, `LEVEL_UP`.

`AchievementManager` implements `GameListener`. Unlocks are persisted immediately with `INSERT ... ON CONFLICT DO NOTHING` and pushed as toasts.
**One manager per session (D23).** `GameEngine` creates an `AchievementManager` for each new or loaded session, seeded with that player's unlocked ids from `MetaRepository.unlockedFor(playerId)`, and subscribes it to that session's `EventBus`. The rules map is a single `static final EnumMap` shared by every manager, because rules are stateless.
Achievement rules are Java code (Strategy objects keyed by `achievement_id`); titles and descriptions are content. `ContentImporter` checks that every achievement in `world.json` except `ALL_ENDINGS` has a Java rule, and vice versa.

| id | Title | Rule |
|---|---|---|
| FIRST_LIGHT | First Light | Obtain `heartshard` |
| FIRST_BLOOD | First Blood | First `COMBAT_WON` |
| SHORT_TALE | A Short Tale | Reach the quick ending |
| SILVER_TONGUE | Silver Tongue | Pass 3 reputation/affinity-locked choices (counted in reserved flag `_locked_passed`, D15) |
| ARMORY | Armory | Own 5 weapons |
| S_RANK | S-Rank | Equip an S-tier or higher weapon |
| CRYSTAL_EDGE | Crystal Edge | Obtain `aetherion` |
| SPEEDRUNNER | Speedrunner | Any ending in under 360 seconds |
| ALL_ENDINGS | All Endings | DB trigger |

### 6.9 Endings
`EndingResolver` walks the endings with `via_resolver = true` by priority; the first whose `ConditionGroup` passes wins. The last of them has no conditions, so the resolver can never fail. Endings with `via_resolver = false` (only `END_QUICK`) are reached by a normal choice edge and never checked by the resolver (D11). On reaching an ending:
- upsert `player_ending`, keeping the lowest `best_seconds`
- publish `ENDING_REACHED`
- set mode to `ENDED`

| Priority | id | via_resolver | Title | Conditions |
|---|---|---|---|---|
| 1 | END_TRUE | true | Aether Reborn | grp 0: `q_shard_one`, `q_shard_two`, `q_shard_three` COMPLETED; ITEM `aetherion` ≥ 1; REP ELDMERE, SYLVAN, CROWN, ASHEN each ≥ 0 |
| 2 | END_TYRANT | true | The Shard Tyrant | grp 0: FLAG `used_dark_power` ≥ 3 |
| 3 | END_ASHEN | true | Ashen Dawn | grp 0: REP ASHEN ≥ 20 — **or** — grp 1: FLAG `gave_shard_to_cult` = 1 (D4) |
| 4 | END_GUARDIAN | true | Guardian of Valdren | grp 0: REP CROWN ≥ 30 AND REP ELDMERE ≥ 30 |
| 5 | END_WANDERER | true | The Wanderer | none (fallback) |
| 99 | END_QUICK | **false** | The Merchant's Bargain | none — reached directly in the prologue (D11) |

Exactly 6 `ending` rows exist, so "Endings found X/6" and the `ALL_ENDINGS` trigger count stay correct.

### 6.10 Save and load
- Slots 1–3 are manual; slot 0 is the autosave, written whenever the player enters a node of a new chapter (D17).
- **Autosave (D24):** on the request thread, inside `synchronized (state)`: (1) `reseedForSave()` on the **live** state, (2) `deepCopy()`, (3) submit the copy to a single-thread `ExecutorService`. See §6.3.
- **Manual save (D22):** `SaveController` → `GameEngine.save(s, slot)`, which locks, rejects saves outside STORY mode with `ActionNotAllowedException` (409), reseeds the live state and calls `SaveRepository.save`. See §4.4.
- **Load (D22):** `SaveController` → `GameEngine.load(playerId, slot)`:
  1. `SaveRepository.load` returns `SaveData` (raw rows) or throws `SaveNotFoundException` (404).
  2. `GameEngine` builds a new `GameState`: stats from the slot row (max hp, atk, def, spd, max energy derived from `level` — D6, D28), inventory from item ids via the in-memory catalog, flags, reputation, affinity, quests, history, `demoMode` (D25), `new Random(rngSeed)`.
  3. It creates a fresh `EventBus`, the toast listener and a new `AchievementManager` seeded from the DB (D23).
  4. It re-enters `current_node` as a view only: no effects re-applied, no `NODE_ENTERED` side effects, no autosave.
  5. The controller stores the new state in `SessionStore` under the caller's session id.
- Saving is only allowed in STORY mode (409 otherwise, D27).

---

## 7. Story

**World:** the Kingdom of Valdren. After a storm, ruins near the village of Eldmere reveal the **Heartshard**, one piece of a broken artifact. The Ashen Circle cult wants it, and so does the King.

| Ch | Region / location | Core beat | Bottleneck |
|---|---|---|---|
| 0 | Eldmere | Find the crystal. Hub: Elder Maren / Merchant Corvin / Hide it. The merchant path leads to the quick ending | Cult raid |
| 1 | Eldmere | Who do you save in the raid? Side quests for E/D/C weapons | Leaving Eldmere |
| 2 | Whispering Wood | Earn the Sylvan Wardens' trust or force through; Shard Quest I | Warden Gate |
| 3 | Hollow Deep | Vault puzzle, dungeon boss, Shard Quest II, hidden SS route | Escape the Deep |
| 4 | Valdren City | Court intrigue, tournament (S), cult mole, Shard Quest III | Summons to the Spire |
| 5 | Crystal Spire (region `SPIRE`, D19) | Final choices + final boss → RESOLVER | Ending |

Target: 70–90 nodes. Prologue about 12; each chapter 12–15.

**Phase 1 stubs (D2, D3).** Until Phase 9, the content must still import cleanly:
- `ch0_prologue.json`: the non-merchant branches from p03 (Elder, Hide, Wardens) fold into the "Cult raid" bottleneck node, which for now has one choice leading to the RESOLVER node. In Phase 9 that choice is re-pointed to Ch1.
- `ch5_finale.json`: only the RESOLVER node and the 5 resolver ending nodes (`END_TRUE` … `END_WANDERER`) with placeholder text, so every `ending.node_id` FK is satisfied. `END_QUICK` lives in the prologue file. The rest of Ch5 is written in Phase 9.

### 7.1 Quick ending route (build first: the vertical slice)

| Node | What happens | Systems shown |
|---|---|---|
| p01 | Storm night in Eldmere | Typewriter, region theme |
| p02 | Ruins: find the crystal. The "Take the crystal" choice carries the effects (D8) | GIVE_ITEM heartshard, START_QUEST q_strange_light, FIRST_LIGHT |
| p03 | Village square: Elder / Merchant / Hide / 🔒 "Ask the Wardens" (FLAG met_wardens = 1) | Locked choice + reason |
| p04m | Corvin offers 300 gold: accept / 🔒 demand 500 (AFFINITY corvin ≥ 10) / walk away | Affinity condition, GOLD |
| p05m | Sold. That night you spot Corvin's Ashen tattoo and follow him | TAKE_ITEM, REP ASHEN |
| p06m | Pause before the alley (prompt the player to save) | Save transaction |
| p07m | COMBAT: Corvin's bodyguard (`enc_corvin_guard`, 1 easy enemy) | Combat, seeded RNG |
| p08m | Loot the guard. The "Take the hatchet" choice gives Woodcutter's Hatchet (E) (D8). Equip? | Tier upgrade, equip |
| p09m | Confront Corvin: take the crystal back by force / let him flee | REP, COMPLETE or FAIL quest |
| p10m | The crystal reacts; flames over Eldmere (location `eldmere_burning`, region VILLAGE) | Location accent via `body[data-location]` (D19) |
| END_QUICK | The Merchant's Bargain | player_ending row, SHORT_TALE, leaderboard |

The route must take 6–8 minutes of play including reading.

---

## 8. Content format

### 8.1 `content/world.json`
Top-level arrays: `factions`, `locations`, `npcs`, `items` (with nested `weapon` or `consumable` objects), `enemies`, `encounters` (with `enemies: [{id, qty}]`), `quests` (with `prereqs: []`), `achievements`, `endings` (with `node`, `priority`, `viaResolver` (default `true`), `conditions`; each condition may carry an optional `grp`, default 0).

### 8.2 Chapter files

```json
{
  "chapter": 0,
  "nodes": [
    {
      "id": "p03",
      "location": "eldmere_square",
      "type": "STORY",
      "title": "The Square at Dawn",
      "text": "80–160 words, second person, present tense",
      "encounter": null, "win": null, "lose": null,
      "choices": [
        {
          "label": "Take it to Elder Maren",
          "to": "p04e",
          "hiddenWhenLocked": false,
          "conditions": [],
          "effects": [ { "type": "REP", "key": "ELDMERE", "value": 5 } ]
        },
        {
          "label": "Ask the Wardens",
          "to": "p04w",
          "conditions": [ { "type": "FLAG", "key": "met_wardens", "op": "=", "value": 1 } ],
          "effects": []
        },
        {
          "label": "Call in a favour",
          "to": "p04f",
          "hiddenWhenLocked": true,
          "conditions": [
            { "type": "AFFINITY", "key": "maren",         "op": ">=", "value": 20,          "grp": 0 },
            { "type": "QUEST",    "key": "q_strange_light", "op": "=", "value": "COMPLETED", "grp": 1 }
          ],
          "effects": []
        }
      ]
    }
  ]
}
```

Field rules:
- `grp` is optional (default 0). Same `grp` = AND, different `grp` = OR (D4). The third choice above (illustrative only, not part of the real prologue) means "affinity with Maren ≥ 20 **or** the quest is completed".
- QUEST conditions write the status **name**; the importer stores its ordinal (D13).
- `sort_order` is the choice's index in the array; `hiddenWhenLocked` defaults to `false`.
- COMBAT nodes set `encounter`, `win`, `lose` and optionally `flee` (D9). Other node types leave all four `null`.
- Nodes have no effects of their own; only choices do (D8).

### 8.3 ContentImporter
- Reads all JSON files and validates every referenced id.
- Runs `StoryValidator` and refuses to import on errors (warnings are printed but do not block).
- Inside **one transaction** (D1):
  1. **Upsert** every content row on its natural key (`INSERT … ON CONFLICT (…) DO UPDATE`). Choices upsert on `(from_node, sort_order)` with `RETURNING choice_id`, so existing ids — and the saves that point at them — stay valid.
  2. **Replace owned child rows**: `choice_condition`, `choice_effect`, `encounter_enemy`, `quest_prereq` and `ending_condition` for each upserted parent are deleted and batch-inserted again (no player table references them).
  3. **Delete content rows no longer in the JSON** (children before parents). Before each delete, the importer queries the player tables for references. If a save still points at a row, the whole import is rolled back with a message that names the row and the save, for example:
     `Import aborted: node 'p05m' was removed from the JSON but save #12 (player 'alice', slot 1) still references it. Delete that save or keep the node.`
     A foreign-key violation (SQLState `23503`) that slips past the pre-check is caught and reported the same way.
  4. Deferred FKs allow forward references between nodes; they are checked at `commit()`.
- Prints a summary: nodes/choices/endings inserted, updated and deleted, plus the validator report.

### 8.4 StoryValidator reports
All checks run on the structural graph from §5 (D10): choice edges **plus** COMBAT → win/lose/flee and RESOLVER → every `via_resolver` ending node.

| # | Check | Severity |
|---|---|---|
| 1 | Nodes unreachable from `p01` (BFS) | error |
| 2 | Non-ENDING nodes with no outgoing structural edges (DFS) | error |
| 3 | COMBAT nodes missing win/lose | error |
| 4 | Endings unreachable from the start | error from Phase 9; warning before |
| 5 | Shortest path length from `p01` to `END_QUICK` (must be ≤ 12) | error |
| 6 | Choices pointing to missing nodes, and unknown item/quest/faction/npc/encounter ids | error |
| 7 | Flags **read** by a condition but never **set** by any `SET_FLAG` effect (D12) | warning |
| 8 | Flags **set** by `SET_FLAG` but never **read** by any condition (D12) | warning |
| 9 | `SET_FLAG` on a reserved `_`-prefixed flag (D15) | error |
| 10 | Quest transitions detectable statically (D14): (a) quest effects naming unknown quests; (b) `COMPLETE_QUEST` / `FAIL_QUEST` on a quest that no `START_QUEST` effect anywhere starts; (c) `START_QUEST` on a quest with a prerequisite that no `COMPLETE_QUEST` effect anywhere completes (it could never become AVAILABLE) | error |

Reserved `_` flags are excluded from checks 7 and 8. Reachability is structural (conditions ignored). `ConsoleRunner --auto-quick` plays the quick route with real conditions as an integration check.

---

## 9. API and frontend

### 9.1 Endpoints (all JSON; session via the `X-Session` header)

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | /api/profiles | `{username}` | `{playerId}` — creates the profile, or returns the existing one for that username (S4) |
| POST | /api/game/new | `{playerId, demoMode}` | GameView with `sessionId` in the body (D18) |
| GET | /api/game | — | GameView |
| POST | /api/game/choose | `{choiceId}` | GameView |
| POST | /api/combat/action | `{type, target, itemId}` | GameView |
| POST | /api/inventory/equip | `{itemId}` | GameView |
| POST | /api/inventory/use | `{itemId}` | GameView |
| GET | /api/saves | — | slots list |
| POST | /api/saves/{slot} | — | ok / error |
| POST | /api/saves/{slot}/load | — | GameView |
| DELETE | /api/saves/{slot} | — | ok |
| GET | /api/achievements | — | all + unlocked |
| GET | /api/endings | — | all + reached |
| GET | /api/leaderboard | — | rows from `v_leaderboard` |
| GET | /api/debug/inspector | — | node id, flags, rep, affinity, quests in topo order, last 5 SQL |

`SaveController` calls only `GameEngine` (`save`, `load`, and the thin pass-throughs `listSaves`, `deleteSave`) (D22).

**Errors:** `ErrorHandler` maps exceptions to `{error, message}`:
- `InvalidChoiceException` → 400
- `LockedChoiceException` → 403
- `SaveNotFoundException` → 404 — loading or deleting an empty slot (D27)
- `ActionNotAllowedException` → 409 — the request is valid but the current mode forbids it: saving outside STORY mode, a choice during combat, a combat action outside combat, FLEE when the encounter forbids it, SKILL with no energy, using an item that can't be used now (D27)
- `SaveFailedException` → 500

### 9.2 GameView

```json
{
  "sessionId": "5b1f…",
  "mode": "STORY",
  "region": "VILLAGE",
  "location": "eldmere_square",
  "node": { "id": "p03", "title": "The Square at Dawn", "text": "..." },
  "choices": [
    { "id": 12, "label": "Take it to Elder Maren", "locked": false },
    { "id": 14, "label": "Ask the Wardens", "locked": true, "lockReason": "Requires flag: met_wardens" }
  ],
  "hud": { "hp": 40, "maxHp": 40, "energy": 3, "maxEnergy": 3, "gold": 15, "level": 1,
           "xp": 20, "xpToNext": 100, "weapon": { "name": "Rusty Shortsword", "tier": "F" } },
  "toasts": [ { "kind": "ITEM", "text": "Obtained: Heartshard" },
              { "kind": "ACHIEVEMENT", "text": "First Light" } ],
  "combat": null,
  "ending": null,
  "offerReload": false
}
```

**Fields added by later phases (D18).** The sample above is the Phase 3 shape. Each phase adds what its panels need, always computed in Java:

| Field | Phase | Contents |
|---|---|---|
| `inventory` | 4 | items with id, name, type, qty, equipped, tier (weapons sorted by `weaponsByTier()`) |
| `quests` | 6 | quests in `topoOrder()` with id, title, status, isMain |
| `reputation`, `affinity` | 6 | faction/NPC values for ReputationPanel |
| `journal` | 7 | recent history steps (node title + choice label), newest first |
| `offerReload` | 5 | `true` only right after a boss loss (S3) |

`combat` (when mode = COMBAT) contains: `enemies [{index, name, hp, maxHp, defending}]`, `log [..]`, `canFlee`, `usableItems [..]`, `lastHits [{targetIndex, amount, crit}]` (for floating damage numbers).

`ending` (when mode = ENDED) contains: `id`, `title`, `text`, `seconds`, `endingsFound`, `endingsTotal`.

### 9.3 Frontend rules and feel
- **No game logic in React.** `GameContext` (`useReducer`) stores the latest GameView; components render it.
- **Typewriter** text, skippable on click.
- **Region themes** via `body[data-region]` CSS variables with a crossfade: warm village, green forest, dark dungeon, gold kingdom, pale crystalline spire (D19).
- **Location accents** via `body[data-location]` for one-off scenes such as `eldmere_burning` (D19). This is presentation only; the location id comes from `GameView`.
- **Tier styles:**

  | Tier | Style |
  |---|---|
  | SSS | Animated rainbow gradient border plus glow |
  | SS | Gold shimmer |
  | S | Purple |
  | A | Blue |
  | B | Green |
  | C and below | Greys |

- **Combat:** animated HP bars, floating damage numbers, screen shake on crit, enemy hit flash.
- **Toasts:** slide in; achievements get a gold style.
- **Locked choices:** greyed out, with a lock icon and a reason tooltip.
- **Ending screen:** stats and an "Endings found X/6" bar.
- **InspectorPanel:** toggled with `~`, shows `/api/debug/inspector` data.
- **Settings:** Demo Mode toggle on the title screen.
- Keep each component under about 120 lines.

---

## 10. Build phases and acceptance criteria

| # | Phase | Acceptance criteria |
|---|---|---|
| 0 | Design | Repo scaffolded (folders, `pom.xml`, Maven wrapper, Vite app, `.gitignore`, `db.properties.example`). ER and class diagram sources in `docs/diagrams/` (Mermaid). PRD, TECH_STACK, ARCHITECTURE, DECISIONS written; SYLLABUS_MAPPING and ALGORITHMS as skeletons, filled at the end of every phase |
| 1 | Database | `01`–`03` SQL run cleanly on an empty DB; `99_reset.sql` drops everything; `world.json` + `ch0_prologue.json` written for the quick route, plus the stub `ch5_finale.json` (D2, D3) |
| 2 | Engine in console | `Graph`, `StoryGraph`, `StoryValidator`, rules (incl. `ConditionGroup`), `GameState`, `ContentRepository`, `ContentImporter`, `ConsoleRunner`. **Prologue playable in the terminal.** `GraphTest`, `StoryValidatorTest`, `ConditionTest`, `QuestStatusOrderTest`, `MaxHpFormulaTest` green. Depends on the student-written `Graph.bfs/dfs/shortestPath` (S8) |
| 3 | API + React shell | Javalin endpoints for profile/new/state/choose; React story, choices, HUD, toasts. Prologue playable in the browser |
| 4 | Inventory & tiers | `Item` hierarchy, `Inventory`, equip/use, InventoryPanel with tier styles. `InventoryTest` green |
| 5 | Combat | `CombatEngine`, `DamageCalculator`, AI, CombatScreen. p07m fight works; `DamageCalculatorTest` green (seeded) |
| 6 | Quests | `QuestManager` with topological sort, quest log. `QuestManagerTest` green (incl. cycle detection) |
| 7 | Save/load | `SaveRepository` transaction, autosave thread, SaveLoadModal, crash simulation. `SaveRepositoryIT` green via `mvnw verify` on `shardbound_test` (S6); save → restart server → load works |
| 8 | Endings + achievements | `EndingResolver`, EventBus, `AchievementManager`, leaderboard. **Quick ending fully playable and demo-safe** |
| 9 | Content | Ch1–Ch5 written; validator reports all 6 endings reachable |
| 10 | Polish | Inspector, Demo Mode, animations, `GraphExporter` → `docs/story-graph.svg`, README with setup steps |

---

## 11. Viva-supporting features (must exist)
- Inspector panel with live flags, reputation, quest topo order, and last SQL
- Save crash simulation proving rollback
- `GraphExporter` story graph image
- Seeded combat + Demo Mode
- 3 prepared save slots: quick-route start, Ch3 boss, late game with an SS weapon
- All SQL in `database/` readable and commented

---

## Appendix A — Content-writing prompts (use with Claude in chat)

### A1. Story bible
```
You are a narrative designer for a turn-based, choice-driven fantasy RPG called "Shardbound".
Premise: a young villager in Eldmere finds the Heartshard, an ancient crystal, after a storm.
Regions: village (Eldmere), forest (Whispering Wood), dungeon (Hollow Deep), kingdom (Valdren City),
finale (Crystal Spire). Factions: ELDMERE, SYLVAN, CROWN, ASHEN (crystal cult).

Write a story bible containing:
1. World lore (max 300 words) and the crystal's secret.
2. 8–10 named NPCs: name, faction, location, one-line personality, what they want from the player.
3. Chapter outline (Ch0–Ch5) using BRANCH-AND-BOTTLENECK structure: for each chapter list
   2–3 branches and the bottleneck node they fold back into.
4. Story FLAGS (snake_case) with what sets them and what reads them.
5. Quests: id, title, giver NPC, prerequisites, reward — use the weapon ladder in spec §6.4 exactly.
6. Endings exactly as in spec §6.9; write their narrative outcomes.
7. Quick ending as in spec §7.1, expanded into scene summaries.
Tone: hopeful dark fantasy, PG-13. Be concrete.
```

### A2. Chapter JSON
```
Using the story bible below, write Chapter {N} ({region}) as JSON matching spec §8.2 exactly.
No prose outside JSON.
RULES:
- 12–15 nodes. Every STORY node has 2–4 choices. No dead ends except ENDING nodes.
- Branches must fold back into the bottleneck node "{bottleneck_id}".
- Use ONLY these existing ids: flags [..], items [..], quests [..], factions [..], npcs [..],
  encounters [..], locations [..]. List any new ids separately under "newIds".
- At least 2 choices must be locked by reputation/affinity/flags set in EARLIER chapters.
- Every reputation change must have a visible narrative reason.
- Exactly one COMBAT node and one quest completion.
Story bible: [paste]
```

### A3. Professor grill
```
Act as a strict professor evaluating my Java-2 / Data Structures / DBMS project. Here is my
schema, class list, and README: [paste]. Ask me 15 hard viva questions one at a time, wait for
my answer, then score it 0–5 and tell me what a perfect answer includes. Focus on normalization,
transactions/ACID, indexes, triggers vs application logic, data structure choices and complexity,
OOP design, concurrency, and SQL injection.
```

---

## 12. Implementation notes (build of Phases 1–8, pending review)

Choices made while writing the code where this spec was silent. Each is tagged **(In)** and explained in `docs/DECISIONS.md`. They are **pending the student's review**; change any of them by editing this section and the code together.

| # | Note |
|---|---|
| I1 | New package `content/`: `RawContent` (rows), `JsonContentReader` (JSON → rows), `ContentAssembler` (rows → objects), `GameContent` (immutable catalog). The DB path (`ContentRepository`) and the JSON path produce the same `RawContent`, so the validator, importer and engine never care where content came from. |
| I2 | Offline mode: `ConsoleRunner --json` and the unit tests load content straight from `content/*.json` with no database; saving is refused (409) and nothing is persisted. `ContentImporter --dry-run` validates without a database. |
| I3 | Five event types were added so that every effect publishes an event (§6.2): `ITEM_LOST`, `FLAG_SET`, `GOLD_CHANGED`, `HEALED`, `XP_GAINED`. Flags produce no toast. |
| I4 | Small helper types: `rules/Rewards` (shared XP/gold/item/consumable granting), `combat/Encounter`, `DamageRoll`, `Hit`, `ActionType`, `item/ItemType`, `persistence/RepositoryException`, `api/Sessions`, `api/dto/Requests` and `MetaViews` (request/response records). |
| I5 | `GiveItemEffect` never throws: a full bag leaves the item behind with a toast, so a choice is never half-applied. Owning a non-stackable item twice is impossible (the save PK is (save_id, item_id)). |
| I6 | Enemies have no weapon; they crit at a fixed 5% (`DamageCalculator.ENEMY_CRIT_PCT`). |
| I7 | `BossAI`: phase 1 = heavy attack every 3rd turn; phase 2 (≤ 50% hp) = heavy attack every 2nd turn. |
| I8 | Level-up adds +5 to current hp as well as max hp. |
| I9 | Extra CHECK constraints in `01_schema.sql` beyond §4.1: non-negative enemy stats/rewards, consumable heal/energy, quest rewards, `play_seconds`, `best_seconds`. |
| I10 | `GET /api/saves`, `/api/achievements`, `/api/endings`, `DELETE /api/saves/{slot}` and `/load` accept `?playerId=` when no `X-Session` is sent (title-screen load). A load without a session creates a new session. An unknown session → 404. |
| I11 | `GameView` already carries the Phase 4–7 fields (`inventory`, `quests`, `reputation`, `affinity`, `journal`, `offerReload`) plus `locationName`, `chapter`, `demoMode`; `CombatView` adds `encounterName`, `round`, `skillCost`, `alive`. |
| I12 | `exec:java` does not compile: use `.\mvnw.cmd compile exec:java -Dexec.mainClass=…`. The default main class is the `exec.mainClass` property in `pom.xml` (plugin `<configuration>` would override `-D`). |
