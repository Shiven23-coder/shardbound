# Shardbound — ER Diagram v2

PostgreSQL holds **player data only** (V2_SPEC §6). Content (story, maps, creeps, bosses, balance) is JSON loaded at startup and has no tables (P-04). Saves and live runs are JSONB documents with a few real columns for what queries need (P-05, P-22). The migrations in `backend/src/main/resources/db/migration/` are authoritative; this file shows their shape.

Keys: **PK**, **FK**, **UK**. Crow's-foot: `||` one, `|o` zero-or-one, `o{` zero-or-many.

| Migration | Phase | Tables |
|---|---|---|
| `V1__baseline.sql` | P0 | player, save_slot, active_run, player_achievement, player_ending, view v_leaderboard |
| `V2__accounts.sql` | V2a | account (replaces player), account_identity, auth_session, login_attempt, account_setting |
| `V3__email_tokens.sql` | V2b | auth_token, email_outbox |
| `V4__feedback.sql` | ALPHA | feedback |
| `V5__telemetry.sql` | V10 | telemetry_event, views, mv_balance_summary |

---

## 1. Baseline (P0)

```mermaid
erDiagram
    player {
        serial player_id PK "becomes account_id in V2a"
        varchar(30) username UK
        timestamptz created_at
    }
    save_slot {
        serial save_id PK
        int player_id FK, UK "CASCADE; UK(player_id, slot_no)"
        smallint slot_no UK "CHECK 0..3; 0 = autosave"
        int save_version "format version of data"
        smallint level "summary column"
        varchar(40) area_id "summary column"
        int playtime_seconds "summary column"
        jsonb data "SaveSnapshot; CHECK jsonb_typeof = object"
        timestamptz created_at
        timestamptz updated_at
    }
    active_run {
        uuid run_id PK
        int player_id FK "CASCADE"
        bytea session_token_hash UK "SHA-256(X-Session) until V2a"
        bigint version "optimistic lock"
        jsonb data "live GameState + optional CombatSnapshot"
        timestamptz updated_at
    }
    player_achievement {
        int player_id PK, FK "CASCADE"
        varchar(40) achievement_id PK "content id, no FK"
        timestamptz unlocked_at
    }
    player_ending {
        int player_id PK, FK "CASCADE"
        varchar(40) ending_id PK "content id, no FK"
        timestamptz first_reached
        int best_seconds
    }

    player ||--o{ save_slot : "owns"
    player ||--o{ active_run : "plays"
    player ||--o{ player_achievement : "unlocked"
    player ||--o{ player_ending : "reached"
```

`v_leaderboard` (view): endings, achievements and fastest ending per player.

---

## 2. Accounts and sessions (V2a)

```mermaid
erDiagram
    account {
        serial account_id PK "was player_id"
        text email UK "lowercase CHECK; NULL for guests and deleted"
        timestamptz email_verified_at "NULL = unverified"
        text password_hash "PHC argon2id; V2b; NULL otherwise"
        varchar(20) display_name UK "case-insensitive unique index"
        boolean is_guest
        smallint failed_logins "V2b"
        timestamptz locked_until "V2b"
        varchar(10) terms_version
        timestamptz terms_accepted_at
        boolean telemetry_opt_out
        timestamptz created_at
        timestamptz last_seen_at
        timestamptz deleted_at "tombstone: PII removed"
    }
    account_identity {
        varchar(20) provider PK "google"
        text subject PK "OIDC sub"
        int account_id FK "CASCADE"
        text email "as reported by the provider"
        timestamptz created_at
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

    account ||--o{ account_identity : "signs in with"
    account ||--o{ auth_session : "has"
    account ||--o{ account_setting : "has"
```

`save_slot`, `active_run`, `player_achievement` and `player_ending` keep their rows; their `player_id` columns now reference `account.account_id`.

---

## 3. Email tokens and outbox (V2b)

```mermaid
erDiagram
    account {
        serial account_id PK
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

    account ||--o{ auth_token : "has"
    account |o--o{ email_outbox : "queued for"
```

---

## 4. Feedback (ALPHA)

```mermaid
erDiagram
    account {
        serial account_id PK
    }
    feedback {
        bigserial feedback_id PK
        int account_id FK "SET NULL"
        varchar(40) area_id "where the player was"
        text message
        varchar(40) client_version
        timestamptz created_at
    }
    account |o--o{ feedback : "sent"
```

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

---

## 6. Design notes
- **Saves and runs are documents.** A save is always read and written whole, so `data JSONB` replaces a dozen child tables; the format evolves through versioned upgrades (`SaveUpgrader`), not schema migrations. Only fields that queries need (`level`, `area_id`, `playtime_seconds`) are real columns, written in the same UPSERT.
- **No player → content foreign keys.** Content ids (`area_id`, `achievement_id`, `ending_id`) are checked against the loaded content instead; a save with unknown ids is refused with `SAVE_INCOMPATIBLE`.
- **Derived stats are never stored.** Max HP/FP, defence, AR and spell power come from attributes, curves and weapons (D6 extended).
- **Optimistic locking** on `active_run.version`: every write-through is `UPDATE … WHERE run_id = ? AND version = ?`; zero rows means `RUN_CONFLICT`.
- Merged migrations are never edited; columns like the `feedback` fields above are settled when their phase is planned.
