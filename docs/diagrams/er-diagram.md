# Shardbound — ER Diagram

Source for `docs/ER-diagram.png` (exported in Phase 10). Matches spec §4.1 including decisions D1, D4, D6, D9, D11, D15, D19, D25, D28, D29, D31.
29 tables: 18 static content, 10 player state, 1 audit log.

Keys: **PK** primary key, **FK** foreign key, **UK** unique. Cardinality uses crow's-foot notation (`||` exactly one, `|o` zero or one, `o{` zero or many, `|{` one or many).

---

## 1. Static content (imported from `content/*.json`)

```mermaid
erDiagram
    faction {
        varchar(20) faction_id PK
        varchar(60) name
    }
    location {
        varchar(40) location_id PK
        varchar(80) name
        varchar(10) region "VILLAGE|FOREST|DUNGEON|KINGDOM|SPIRE (D19)"
    }
    npc {
        varchar(40) npc_id PK
        varchar(60) name
        varchar(20) faction_id FK "nullable"
        varchar(40) location_id FK "nullable"
    }
    item {
        varchar(40) item_id PK
        varchar(80) name
        item_type type "WEAPON|CONSUMABLE|KEY"
        text description
        int price "CHECK >= 0"
    }
    weapon {
        varchar(40) item_id PK, FK "ISA item, CASCADE"
        weapon_tier tier "F < E < ... < SSS"
        int attack "CHECK > 0"
        int crit_pct "CHECK 0..100"
        varchar(80) ability
    }
    consumable {
        varchar(40) item_id PK, FK "ISA item, CASCADE"
        int heal
        int energy
    }
    enemy {
        varchar(40) enemy_id PK
        varchar(60) name
        int hp "CHECK > 0"
        int atk
        int def
        int spd
        int xp_reward
        int gold_reward
        varchar(12) ai "AGGRESSIVE|DEFENSIVE|BOSS"
    }
    encounter {
        varchar(40) encounter_id PK
        varchar(80) name
        boolean can_flee
    }
    encounter_enemy {
        varchar(40) encounter_id PK, FK "CASCADE"
        varchar(40) enemy_id PK, FK
        smallint qty "CHECK 1..4"
    }
    story_node {
        varchar(40) node_id PK
        smallint chapter "CHECK 0..5"
        varchar(40) location_id FK
        node_type type "STORY|COMBAT|RESOLVER|ENDING"
        varchar(120) title
        text body
        varchar(40) encounter_id FK "COMBAT only"
        varchar(40) win_node FK "self, deferrable"
        varchar(40) lose_node FK "self, deferrable"
        varchar(40) flee_node FK "self, deferrable; CHECK NULL or type=COMBAT (D9, D31)"
    }
    choice {
        serial choice_id PK "stable across re-import (D1)"
        varchar(40) from_node FK, UK "CASCADE; UK(from_node, sort_order)"
        varchar(40) to_node FK "deferrable"
        varchar(160) label
        smallint sort_order UK
        boolean hidden_when_locked
    }
    choice_condition {
        int choice_id PK, FK "CASCADE"
        smallint seq PK
        smallint grp "same grp AND, diff grp OR (D4)"
        varchar(10) cond_type "FLAG|REP|AFFINITY|ITEM|QUEST|STAT"
        varchar(40) key
        varchar(2) op "= != > >= < <="
        int value
    }
    choice_effect {
        int choice_id PK, FK "CASCADE"
        smallint seq PK
        varchar(16) effect_type "SET_FLAG|REP|...|XP"
        varchar(40) key
        int value
    }
    quest {
        varchar(40) quest_id PK
        varchar(80) title
        text description
        varchar(40) giver_npc FK "nullable"
        varchar(40) reward_item FK "nullable"
        int reward_xp
        int reward_gold
        boolean is_main
    }
    quest_prereq {
        varchar(40) quest_id PK, FK "CASCADE"
        varchar(40) requires_id PK, FK "CASCADE; CHECK <> quest_id"
    }
    achievement {
        varchar(40) achievement_id PK
        varchar(80) title
        text description
        boolean hidden
    }
    ending {
        varchar(40) ending_id PK
        varchar(40) node_id FK, UK "= ending_id by convention"
        varchar(80) title
        smallint priority UK "lower checked first"
        boolean via_resolver "FALSE for END_QUICK (D11)"
    }
    ending_condition {
        varchar(40) ending_id PK, FK "CASCADE"
        smallint seq PK
        smallint grp "same grp AND, diff grp OR (D4)"
        varchar(10) cond_type
        varchar(40) key
        varchar(2) op
        int value
    }

    faction  |o--o{ npc : "member"
    location |o--o{ npc : "found at"
    location ||--o{ story_node : "set in"

    item ||--o| weapon : "is a"
    item ||--o| consumable : "is a"

    encounter ||--o{ encounter_enemy : "contains"
    enemy     ||--o{ encounter_enemy : "appears in"
    encounter |o--o{ story_node : "fought at"

    story_node |o--o{ story_node : "win / lose / flee"
    story_node ||--o{ choice : "from_node"
    story_node ||--o{ choice : "to_node"
    choice ||--o{ choice_condition : "requires"
    choice ||--o{ choice_effect : "applies"

    npc  |o--o{ quest : "gives"
    item |o--o{ quest : "rewards"
    quest ||--o{ quest_prereq : "quest_id"
    quest ||--o{ quest_prereq : "requires_id"

    story_node ||--o| ending : "is"
    ending ||--o{ ending_condition : "requires"
```

---

## 2. Player state (written by the game)

Content entities are repeated here with keys only, to show what player rows point at.

```mermaid
erDiagram
    player {
        serial player_id PK
        varchar(30) username UK
        timestamptz created_at
    }
    save_slot {
        serial save_id PK
        int player_id FK, UK "CASCADE; UK(player_id, slot_no)"
        smallint slot_no UK "0 = autosave, 1..3 manual"
        varchar(40) current_node FK "RESTRICT"
        int hp "CHECK 0 .. 40 + 5*(level-1) (D28)"
        int energy "CHECK >= 0 (D32)"
        int level "CHECK >= 1"
        int xp "progress within level (D29)"
        int gold "CHECK >= 0"
        bigint rng_seed "fresh seed drawn at save (D7)"
        boolean demo_mode "default FALSE (D25)"
        int play_seconds
        int version "+1 per overwrite"
        timestamptz updated_at "trigger touch_save"
    }
    save_inventory {
        int save_id PK, FK "CASCADE"
        varchar(40) item_id PK, FK "RESTRICT"
        int qty "CHECK > 0"
        boolean equipped "partial UNIQUE: one per save"
    }
    save_flag {
        int save_id PK, FK "CASCADE"
        varchar(40) flag_key PK "'_' prefix = engine-reserved (D15)"
        int value
    }
    save_reputation {
        int save_id PK, FK "CASCADE"
        varchar(20) faction_id PK, FK "RESTRICT"
        int value "CHECK -100..100"
    }
    save_affinity {
        int save_id PK, FK "CASCADE"
        varchar(40) npc_id PK, FK "RESTRICT"
        int value "CHECK -100..100"
    }
    save_quest {
        int save_id PK, FK "CASCADE"
        varchar(40) quest_id PK, FK "RESTRICT"
        quest_status status
    }
    save_history {
        int save_id PK, FK "CASCADE"
        int step_no PK "1 = oldest (S7)"
        varchar(40) node_id FK "RESTRICT"
        int choice_id FK "SET NULL (D1)"
    }
    player_achievement {
        int player_id PK, FK "CASCADE"
        varchar(40) achievement_id PK, FK "RESTRICT"
        timestamptz unlocked_at
    }
    player_ending {
        int player_id PK, FK "CASCADE"
        varchar(40) ending_id PK, FK "RESTRICT"
        timestamptz first_reached
        int best_seconds
    }
    save_audit {
        serial audit_id PK
        int save_id "no FK: survives deletion"
        varchar(10) action "INSERT|UPDATE"
        varchar(40) node_id
        timestamptz at
    }

    story_node {
        varchar(40) node_id PK
    }
    choice {
        serial choice_id PK
    }
    item {
        varchar(40) item_id PK
    }
    faction {
        varchar(20) faction_id PK
    }
    npc {
        varchar(40) npc_id PK
    }
    quest {
        varchar(40) quest_id PK
    }
    achievement {
        varchar(40) achievement_id PK
    }
    ending {
        varchar(40) ending_id PK
    }

    player ||--o{ save_slot : "owns (max 4)"
    save_slot ||--o{ save_inventory : "holds"
    save_slot ||--o{ save_flag : "has"
    save_slot ||--o{ save_reputation : "has"
    save_slot ||--o{ save_affinity : "has"
    save_slot ||--o{ save_quest : "tracks"
    save_slot ||--o{ save_history : "records"

    story_node ||--o{ save_slot : "current_node"
    item       ||--o{ save_inventory : "item_id"
    faction    ||--o{ save_reputation : "faction_id"
    npc        ||--o{ save_affinity : "npc_id"
    quest      ||--o{ save_quest : "quest_id"
    story_node ||--o{ save_history : "node_id"
    choice     |o--o{ save_history : "choice_id"

    player      ||--o{ player_achievement : "earned"
    achievement ||--o{ player_achievement : "earned by"
    player      ||--o{ player_ending : "reached"
    ending      ||--o{ player_ending : "reached by"

    save_slot ||..o{ save_audit : "logged by trigger (no FK)"
```

---

## 3. Referential actions (D1)

| Child → parent | ON DELETE | Effect |
|---|---|---|
| `weapon`/`consumable` → `item` | CASCADE | subtype row goes with its item |
| `encounter_enemy` → `encounter` | CASCADE | owned rows |
| `choice` → `story_node` (from_node) | CASCADE | a node's choices go with it |
| `choice_condition`, `choice_effect` → `choice` | CASCADE | owned rows |
| `quest_prereq` → `quest` (both columns) | CASCADE | owned rows |
| `ending_condition` → `ending` | CASCADE | owned rows |
| `save_*` children → `save_slot` | CASCADE | deleting a save deletes its contents |
| `save_slot`, `player_achievement`, `player_ending` → `player` | CASCADE | deleting a profile deletes everything it owns |
| every player table → a content table | **RESTRICT** | importer cannot remove content a save still uses; it aborts and names the save |
| `save_history.choice_id` → `choice` | **SET NULL** | a removed choice only blanks one journal label |
| `save_audit` | — (no FK) | audit history survives save deletion |

## 4. Other database objects (spec §4.2–4.3)

| Object | Kind | Purpose |
|---|---|---|
| `weapon_tier`, `node_type`, `item_type`, `quest_status` | ENUM types | ordered value sets shared with Java enums (`QuestStatusOrderTest`, D13) |
| `touch_save` | BEFORE UPDATE trigger on `save_slot` | sets `updated_at` |
| `audit_save` | AFTER INSERT/UPDATE trigger on `save_slot` | writes `save_audit` |
| `all_endings` | AFTER INSERT trigger on `player_ending` | grants `ALL_ENDINGS` when a player has every ending |
| `fn_best_weapon(save_id)` | SQL function | best weapon by enum order, then attack |
| `v_leaderboard` | view | endings, achievements, fastest ending per player |
| `one_equipped_per_save` | partial unique index | at most one equipped item per save |
| `idx_choice_from`, `idx_history_save` | indexes | FK columns used in lookups (Postgres does not auto-index FKs) |

## 5. Normal form notes

- **Every table is in 3NF, with no exceptions**: non-key columns depend on the whole key and nothing but the key.
- `max_hp`, `atk`, `def`, `spd` and `max_energy` are **not** stored in `save_slot`, because each is determined by `level` (a transitive dependency `save_id → level → max_hp`). Java derives them (D6, D28).
- The DB still enforces the hp limit without storing it: `CHECK (hp BETWEEN 0 AND 40 + 5 * (level - 1))` computes the bound from `level` in the same row. `MaxHpFormulaTest` keeps this expression and `Stats.maxHp()` identical (D28).
- `level` and `xp` are both stored and independent: `xp` is progress within the current level and resets on level-up, so neither determines the other (D29).
- `item` / `weapon` / `consumable` is a **generalization (ISA)** with a shared primary key; KEY items need no subtype table.
- Many-to-many relationships are resolved by associative tables: `encounter_enemy`, `quest_prereq`, `player_achievement`, `player_ending`.
