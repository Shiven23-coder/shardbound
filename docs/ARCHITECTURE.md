# Shardbound — Architecture

How the pieces fit together and how one player choice travels from a click to the database and back.
Details of each rule are in `PROJECT_SPEC.md`; reasons for changes are in `DECISIONS.md`.

---

## 1. Layers

```mermaid
flowchart TB
    subgraph Browser["Browser — React + Vite (renderer only)"]
        UI["Screens and components"] --> CTX["GameContext (useReducer)\nholds latest GameView"]
        CTX --> CLIENT["api/client.js\nfetch + X-Session header"]
    end

    CLIENT -- "JSON over HTTP\n/api/* (Vite proxy → :7070)" --> CTRL

    subgraph Server["Java 21 — Javalin 6"]
        CTRL["Controllers\nGame, Combat, Save, Profile, Meta\n+ ErrorHandler"] --> SESS["SessionStore\nConcurrentHashMap&lt;String, GameState&gt;"]
        CTRL --> ENG["GameEngine"]
        ENG --> CE["CombatEngine\n+ DamageCalculator + EnemyAI"]
        ENG --> QM["QuestManager"]
        ENG --> ER["EndingResolver"]
        ENG --> BUS["EventBus (Observer)"]
        BUS --> AM["AchievementManager"]
        ENG --> MEM["In-memory content\nStoryGraph, items, quests, endings"]
        ENG --> AUTO["Autosave executor\n(single thread)"]
        AM --> REPO
        AUTO --> REPO["Repositories — plain JDBC\nSave, Player, Meta, Content + SqlLog"]
        ER --> REPO
    end

    REPO -- "PreparedStatement\nhand-written SQL" --> DB[("PostgreSQL 16\ntables, triggers,\nfunctions, views")]
    IMP["tools/ContentImporter"] -- "reads" --> JSON["content/*.json"]
    IMP -- "one transaction:\nupsert + delete removed" --> DB
    DB -- "loaded once at startup" --> MEM
```

### What lives where

| Concern | Java | React | PostgreSQL |
|---|---|---|---|
| Game rules (conditions, effects, damage, AI, quests, endings) | ✅ all of it | ❌ never | ❌ |
| Story content | in-memory copy (read-only) | ❌ | ✅ authoritative after import |
| Player progress | live `GameState` per session | latest `GameView` only | ✅ saves, achievements, endings |
| Aggregates and bookkeeping | | | ✅ triggers: `updated_at`, audit, `ALL_ENDINGS`; view: leaderboard |
| Presentation (themes, animation, typewriter) | supplies `region`, `location`, data | ✅ | |

---

## 2. Startup sequence

1. `App.main` reads `db.properties` (`AppConfig`) and opens the `Database`.
2. `ContentRepository` loads nodes, choices (with condition groups and effects), items, enemies, encounters, quests, endings and achievements.
3. `StoryGraph` builds the structural `Graph<String, String>` (D10). `QuestManager.topoOrder()` runs Kahn's algorithm and throws `ContentValidationException` on a cycle, so a broken quest DAG stops the server at startup, not mid-game.
4. Javalin registers routes and `ErrorHandler`, and starts on port 7070.

Content is never re-read while the server runs. Re-importing content means: run `ContentImporter`, then restart the server.

---

## 3. A choice, end to end

```mermaid
sequenceDiagram
    autonumber
    actor P as Player
    participant R as React (ChoiceList → GameContext)
    participant C as GameController
    participant S as SessionStore
    participant E as GameEngine
    participant B as EventBus
    participant A as AchievementManager
    participant X as Autosave thread
    participant D as PostgreSQL

    P->>R: click "Take it to Elder Maren"
    R->>C: POST /api/game/choose {choiceId: 12}<br/>X-Session: 5b1f…
    C->>S: get(sessionId)
    S-->>C: GameState s
    Note over E: synchronized (s) { … } inside GameEngine (D16, D22)
    C->>E: choose(s, 12)
    E->>E: graph.choice(currentNode, 12)<br/>missing → InvalidChoiceException (400)
    E->>E: ConditionGroup.test(s)<br/>false → LockedChoiceException (403)
    loop each Effect
        E->>E: effect.apply(s, bus)
        E->>B: publish(REP_CHANGED / ITEM_GAINED / …)
        B->>A: onEvent(e)
        opt rule satisfied
            A->>D: INSERT INTO player_achievement … ON CONFLICT DO NOTHING
        end
        B-->>E: toast queued in s.pendingToasts
    end
    E->>E: history.push(step)
    E->>E: enterNode(s, toNode)
    alt chapter changed (D17)
        E->>E: reseedForSave() on live state, then deepCopy() (D24)
        E-)X: submit(snapshot)
        X->>D: save transaction (slot 0)
    end
    alt ENDING node
        E->>D: upsert player_ending (keep lowest best_seconds)
        D->>D: trigger all_endings → maybe ALL_ENDINGS
    end
    E-->>C: GameView (record)
    C-->>R: 200 JSON
    R->>R: dispatch → re-render story, choices, HUD, toasts
```

Key points for the viva:
- **The server re-checks everything.** The client sends only a `choiceId`; it cannot claim a choice is unlocked.
- **Most choices touch no database at all.** Content is in memory; only achievements, endings and autosaves write.
- **The autosave never blocks the player.** The request thread makes a deep copy and hands it to a single background thread, so the live state and the saved snapshot cannot interfere.

---

## 4. Save transaction

```mermaid
sequenceDiagram
    participant R as SaveRepository.save
    participant D as PostgreSQL
    R->>D: setAutoCommit(false)
    R->>D: INSERT save_slot … ON CONFLICT … DO UPDATE RETURNING save_id
    Note over D: triggers: touch_save (UPDATE), audit_save
    R->>D: DELETE child rows for save_id
    R->>D: batch INSERT save_inventory
    opt simulateSaveCrash
        R--xR: throw → rollback → previous save intact
    end
    R->>D: batch INSERT flags, reputation, affinity, quests, history
    R->>D: commit()
```

Either every row of the new save is written, or none are and the previous save is untouched (atomicity). The student implements this method (CLAUDE.md rule 8).

---

## 5. Content pipeline

```mermaid
flowchart LR
    W["content/world.json\nch0…ch5 JSON"] --> P["ContentImporter\nparse (Jackson)"]
    P --> V["StoryValidator\n10 checks (BFS/DFS)"]
    V -- errors --> X["abort, print report"]
    V -- ok --> T["one transaction"]
    T --> U["1. upsert by natural keys"]
    U --> C["2. replace owned child rows"]
    C --> DEL["3. delete removed rows\n(blocked if a save refers → abort + name it)"]
    DEL --> CM["4. commit\n(deferred FKs checked)"]
```

---

## 6. Package map (backend)

| Package | Responsibility | Patterns |
|---|---|---|
| `config` | `AppConfig`, `Database` | |
| `api`, `api.dto` | HTTP routes, exception → status mapping, `GameView` records | |
| `engine` | `GameEngine`, `GameState`, `SessionStore`, `EndingResolver`, `Mode` | State machine (Mode) |
| `story` | `StoryNode`, `Choice`, `StoryGraph`, `StoryValidator` | |
| `ds` | `Graph<V,E>` | Generics |
| `rules` | `Condition` (sealed), `ConditionGroup`, `Effect`s, `RuleFactory` | Factory, polymorphism |
| `model` | `Faction`, `Npc`, `Stats`, `Tier` | Enums with fields |
| `item` | `Item` hierarchy, `ItemStack`, `Inventory` | Inheritance |
| `quest` | `Quest`, `QuestStatus`, `QuestManager` | State machine |
| `combat`, `combat.ai` | `CombatEngine`, `DamageCalculator`, `EnemyAI`s | Strategy |
| `event`, `achievement` | `EventBus`, `GameListener`, `AchievementManager` | Observer |
| `persistence` | JDBC repositories, `SqlLog` | Repository |
| `tools` | `ContentImporter`, `ConsoleRunner`, `GraphExporter` | |
| `exception` | Custom unchecked exceptions | |

Class-level detail: `docs/diagrams/class-diagram.md`. Tables: `docs/diagrams/er-diagram.md`.

---

## 7. Error handling

| Exception | HTTP | When |
|---|---|---|
| `InvalidChoiceException` | 400 | choice id not on the current node |
| `LockedChoiceException` | 403 | conditions not met |
| `SaveNotFoundException` | 404 | loading or deleting an empty slot (D27) |
| `ActionNotAllowedException` | 409 | valid request the current mode forbids, e.g. saving during combat (D27) |
| `SaveFailedException` | 500 | save rolled back / DB down |
| `ContentValidationException` | — | startup or import only; never during play |

Every error body is `{ "error": "<type>", "message": "<human text>" }`.
