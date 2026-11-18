# Shardbound — ER Diagram v2

v2 additions and changes (V2_SPEC §6). The v1 diagram (`er-diagram.md`) still describes the baseline tables (`V1__baseline.sql`); this file shows what v2 adds or changes. Unchanged v1 entities appear with keys only.

Keys: **PK**, **FK**, **UK**. Crow's-foot: `||` one, `|o` zero-or-one, `o{` zero-or-many.

---

## 1. Accounts, sessions, tokens, email outbox (V2a / V2b)

```mermaid
erDiagram
    account {
        serial account_id PK "was player_id (v1)"
        text email UK "lowercase CHECK; NULL for guests and deleted"
        timestamptz email_verified_at "NULL = unverified"
        text password_hash "PHC argon2id; NULL for guests"
        varchar(20) display_name UK "case-insensitive unique index"
        boolean is_guest
        smallint failed_logins
        timestamptz locked_until
        varchar(10) terms_version
        timestamptz terms_accepted_at
        boolean telemetry_opt_out
        timestamptz created_at
        timestamptz last_seen_at
        timestamptz deleted_at "tombstone: PII removed"
    }
    auth_session {
        bytea token_hash PK "SHA-256 of cookie token"
        int account_id FK "CASCADE"
        timestamptz created_at
        timestamptz last_seen_at
        timestamptz expires_at "30 d idle, sliding"
        timestamptz absolute_expires_at "90 d"
        timestamptz revoked_at
    }
    auth_token {
        bytea token_hash PK "SHA-256"
        int account_id FK "CASCADE"
        auth_purpose purpose "VERIFY | RESET | CHANGE_EMAIL"
        text new_email "CHANGE_EMAIL only"
        timestamptz expires_at "24 h / 30 min"
        timestamptz used_at "single use"
        timestamptz created_at
    }
    login_attempt {
        bigserial attempt_id PK
        inet ip
        text email "as typed, lowercased"
        boolean success
        timestamptz at
    }
    account_setting {
        int account_id PK, FK "CASCADE"
        varchar(40) setting_key PK "text_speed, music_volume, key_bindings..."
        text value
    }
    email_outbox {
        bigserial outbox_id PK
        int account_id FK "SET NULL"
        text to_email
        varchar(30) template "verify | reset | email_changed | account_exists"
        jsonb payload "placeholder values"
        outbox_status status "PENDING | SENT | FAILED"
        smallint attempts
        timestamptz next_attempt_at "backoff 30s·2^n, max 1h"
        text last_error
        timestamptz created_at
        timestamptz sent_at
    }

    account ||--o{ auth_session : "has"
    account ||--o{ auth_token : "has"
    account ||--o{ account_setting : "has"
    account |o--o{ email_outbox : "queued for"
```

---

## 2. World content (V3)

```mermaid
erDiagram
    region {
        varchar(20) region_id PK
        varchar(60) name
        varchar(60) music
    }
    area {
        varchar(40) area_id PK
        varchar(20) region_id FK
        varchar(60) name
        varchar(60) map_key "content/maps/<key>.tmj"
        varchar(60) music "nullable: region default"
    }
    area_edge {
        varchar(40) from_area PK, FK
        varchar(40) to_area PK, FK
        int weight "walking seconds, CHECK > 0"
        varchar(40) gate_flag "nullable: needs this flag"
    }
    area_collision {
        varchar(40) area_id PK, FK "CASCADE"
        smallint width
        smallint height
        bytea bits "BitSet, index y*width + x"
    }
    map_object {
        varchar(60) object_id PK "area:tiledId"
        varchar(40) area_id FK "CASCADE"
        varchar(20) type "spawn_point | exit | creep_spawn | npc | lantern | smith | chest | trigger"
        smallint x
        smallint y
        jsonb props "type-specific (template, levelMin..)"
    }
    lantern {
        varchar(40) lantern_id PK
        varchar(60) object_id FK, UK
        varchar(60) name
    }
    dialogue {
        varchar(40) dialogue_id PK
        varchar(40) npc_id FK
        varchar(40) start_node
    }
    dialogue_node {
        varchar(40) node_id PK
        varchar(40) dialogue_id FK "CASCADE"
        varchar(60) speaker
        varchar(60) portrait
        varchar(10) mood
        text text
    }
    dialogue_choice {
        serial choice_id PK "stable via UK (from_node, sort_order), D1"
        varchar(40) from_node FK, UK
        smallint sort_order UK
        varchar(40) to_node FK "NULL = end"
        varchar(160) label
        boolean hidden_when_locked
    }
    dialogue_choice_condition {
        int choice_id PK, FK
        smallint seq PK
        smallint grp "D4"
        varchar(10) cond_type
        varchar(40) key
        varchar(2) op
        int value
    }
    dialogue_choice_effect {
        int choice_id PK, FK
        smallint seq PK
        varchar(16) effect_type
        varchar(40) key
        int value
    }
    npc {
        varchar(40) npc_id PK
    }

    region ||--o{ area : "contains"
    area ||--o{ area_edge : "from"
    area ||--o{ area_edge : "to"
    area ||--|| area_collision : "has"
    area ||--o{ map_object : "places"
    map_object ||--o| lantern : "is"
    npc ||--o{ dialogue : "speaks"
    dialogue ||--o{ dialogue_node : "has"
    dialogue_node ||--o{ dialogue_choice : "offers"
    dialogue_choice ||--o{ dialogue_choice_condition : "requires"
    dialogue_choice ||--o{ dialogue_choice_effect : "applies"
```

---

## 3. Battle content (V4, V6, V7)

```mermaid
erDiagram
    creature_family {
        varchar(20) family_id PK
        varchar(40) name
    }
    family_element {
        varchar(20) family_id PK, FK
        element element PK "PHYSICAL..CRYSTAL"
        weakness_grade grade "W | N | R | I"
    }
    family_status_immunity {
        varchar(20) family_id PK, FK
        status status PK "BLEED..STUN"
    }
    skill {
        varchar(40) skill_id PK
        varchar(60) name
        element element
        numeric power
        smallint fp_cost
        varchar(8) scaling "WEAPON | MIND | ARCANE"
        varchar(4) target "ONE | ALL"
        status status "nullable"
        smallint status_chance
    }
    creep_template {
        varchar(40) template_id PK
        varchar(60) name
        varchar(20) family_id FK
        varchar(60) sprite
        int base_hp
        int base_atk
        int base_def
        smallint base_spd
    }
    creep_skill {
        varchar(40) template_id PK, FK
        varchar(40) skill_id PK, FK
        smallint weight
    }
    boss {
        varchar(40) boss_id PK
        varchar(60) name
        varchar(20) family_id FK
        smallint level
        varchar(40) arena_area FK
        varchar(40) intro_cutscene FK
    }
    boss_phase {
        varchar(40) boss_id PK, FK
        smallint phase PK "1 | 2"
        smallint hp_pct_at "phase 2 threshold"
        text transition_line
        varchar(20) family_override "nullable"
        smallint spd_delta
    }
    boss_move {
        varchar(40) boss_id PK, FK
        smallint phase PK, FK
        varchar(40) skill_id PK, FK
        smallint weight
        text telegraph "nullable: announced a turn early"
    }
    cutscene {
        varchar(40) cutscene_id PK
        boolean skippable
    }
    cutscene_step {
        varchar(40) cutscene_id PK, FK
        smallint seq PK
        varchar(10) kind "say | move | camera | music | wait | fade"
        jsonb args
    }
    weapon {
        varchar(40) item_id PK, FK
        weapon_tier tier
        int base_attack "NEW"
        smallint crit_pct
    }
    weapon_scaling {
        varchar(40) item_id PK, FK
        attribute attribute PK "STR | DEX | ARC"
        scaling_grade grade "S..E"
    }
    upgrade_cost {
        smallint from_level PK "0..9"
        int shards "150*(u+1)^2"
        varchar(40) material_item FK
        smallint material_qty
    }
    curve_point {
        varchar(30) curve_id PK "vigor_hp, mind_fp, sat, ..."
        smallint x PK
        numeric y
    }

    creature_family ||--o{ family_element : "chart row"
    creature_family ||--o{ family_status_immunity : "immune to"
    creature_family ||--o{ creep_template : "classifies"
    creep_template ||--o{ creep_skill : "uses"
    skill ||--o{ creep_skill : "used by"
    boss ||--o{ boss_phase : "has"
    boss_phase ||--o{ boss_move : "moves"
    skill ||--o{ boss_move : "is"
    cutscene ||--o{ cutscene_step : "steps"
    cutscene |o--o{ boss : "intro of"
    weapon ||--o{ weapon_scaling : "scales with"
```

---

## 4. Saves v2 (V5–V9)

```mermaid
erDiagram
    save_slot {
        serial save_id PK
        int account_id FK, UK "CASCADE"
        smallint slot_no UK "0 autosave, 1..3"
        int save_version "CHECK >= 1; current 2"
        uuid run_id "anonymous telemetry key"
        varchar(40) area_id FK "RESTRICT"
        smallint x
        smallint y
        char(1) facing
        int hp "CHECK 0..curve(vigor) enforced in Java + range CHECK"
        int fp
        int shards "CHECK >= 0"
        smallint draught_charges
        varchar(40) last_lantern FK
        bigint rng_seed
        boolean demo_mode
        int play_seconds
        int version
        timestamptz updated_at
    }
    save_attribute {
        int save_id PK, FK "CASCADE"
        attribute attribute PK "VIG MND STR DEX END ARC"
        smallint value "CHECK 10..99"
    }
    save_weapon {
        int save_id PK, FK
        varchar(40) item_id PK, FK
        smallint upgrade "CHECK 0..10"
    }
    save_skill_loadout {
        int save_id PK, FK
        smallint slot PK "1..4"
        varchar(40) skill_id FK
    }
    save_quick_slot {
        int save_id PK, FK
        smallint slot PK "1..4"
        varchar(40) item_id FK
    }
    save_lost_shards {
        int save_id PK, FK "at most one"
        varchar(40) area_id FK
        smallint x
        smallint y
        int amount "CHECK > 0"
    }
    save_defeated_creep {
        int save_id PK, FK "cleared on rest/death"
        varchar(60) object_id PK, FK
    }
    save_defeated_boss {
        int save_id PK, FK
        varchar(40) boss_id PK, FK
    }
    save_discovered_lantern {
        int save_id PK, FK
        varchar(40) lantern_id PK, FK
    }
    save_cutscene_seen {
        int save_id PK, FK
        varchar(40) cutscene_id PK, FK
    }
    save_bestiary {
        int save_id PK, FK
        varchar(40) template_id PK, FK
        timestamptz seen_at
        boolean stats_revealed
        boolean weakness_revealed
    }
    save_quest_step {
        int save_id PK, FK
        varchar(40) quest_id PK, FK
        smallint step PK
        timestamptz done_at
    }
    account {
        serial account_id PK
    }

    account ||--o{ save_slot : "owns"
    save_slot ||--o{ save_attribute : "has"
    save_slot ||--o{ save_weapon : "has"
    save_slot ||--o{ save_skill_loadout : "has"
    save_slot ||--o{ save_quick_slot : "has"
    save_slot ||--o| save_lost_shards : "may have"
    save_slot ||--o{ save_defeated_creep : "has"
    save_slot ||--o{ save_defeated_boss : "has"
    save_slot ||--o{ save_discovered_lantern : "has"
    save_slot ||--o{ save_cutscene_seen : "has"
    save_slot ||--o{ save_bestiary : "has"
    save_slot ||--o{ save_quest_step : "has"
```

v1 child tables `save_inventory`, `save_flag`, `save_reputation`, `save_affinity`, `save_quest` and `save_history` stay unchanged. `current_node` is dropped after the v1→v2 migration (V10__saves_v2).

---

## 5. Telemetry (V10)

```mermaid
erDiagram
    telemetry_event {
        bigserial event_id PK
        uuid run_id "no account link (anonymous)"
        varchar(20) type "RUN_START | AREA_ENTER | BATTLE_START | BATTLE_END | DEATH | ..."
        varchar(40) area_id
        smallint level
        jsonb payload
        timestamptz at "BRIN(at); B-tree(run_id, at)"
    }
    mv_balance_summary {
        varchar(40) area_id UK "unique index → REFRESH CONCURRENTLY"
        int runs
        numeric death_rate
        numeric median_fights
        numeric median_entry_level
    }
    telemetry_event }o..|| mv_balance_summary : "aggregated into"
```

Views: `v_deaths_by_area`, `v_time_per_area` (`LEAD(at) OVER (PARTITION BY run_id ORDER BY at)`), `v_fights_per_level` (`ROW_NUMBER()`, `SUM() OVER`).

## 6. Normal-form notes (v2)
- Still 3NF. Max HP/FP, defence, AR and spell power are **derived** from attributes, curves and weapons, never stored (D6/D28 extended).
- `save_slot.hp` keeps a plain range CHECK (`hp >= 0`); the curve-based upper bound is enforced in Java, because a CHECK cannot read `curve_point` rows (V-15).
- `map_object.props` is JSONB because each object type has different properties. It is the one deliberate semi-structured column; everything the server queries by (type, area, x, y) is a real column.
