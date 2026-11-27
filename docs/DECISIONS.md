# Shardbound — Decision Log

Why the specs say what they say. `docs/V2_SPEC.md` is the source of truth for **what** to build (`docs/PROJECT_SPEC.md` is the historical v1 spec); this file records **why** each change was made. Decisions are tagged in the specs as **(Dn)**, **(Sn)**, **(V-nn)** or **(P-nn)** so you can jump between them.

- **D1–D19** came from the v1 spec review (contradictions and gaps).
- **S1–S9** are the smaller points approved at the same time.
- **D20–D32** came from the review of the v1 ER and class diagrams.
- **I1–I12** are implementation choices made while building v1.
- **V-01 … V-35** are the v2 design decisions.
- **P-01 … P-57** are the platform reset (Phase P0) and its follow-ups: the project became a public game, and these entries win over anything older.
- Old entries are never deleted. Where a later decision changes an earlier one, the earlier entry says so ("Revised by" or "Superseded by").

Format for each entry: **Problem** → **Decision** → **Why** → **Where in spec**.

---

## D1 — Content re-import must not break saves

**Problem.** The importer "deleted existing content tables" before re-inserting, but player tables reference content (`save_slot.current_node`, `save_history`, `save_inventory`, `player_ending`, …). Any re-import after the first save would fail on a foreign key. `choice_id SERIAL` also meant every re-import gave choices new ids, silently corrupting saved history.

**Decision.**
1. Upsert every content row on its natural key (`ON CONFLICT … DO UPDATE`). Choices upsert on `UNIQUE (from_node, sort_order)` and keep their `choice_id`.
2. Delete content rows that are no longer in the JSON.
3. Every player→content FK is `ON DELETE RESTRICT` (written explicitly), except `save_history.choice_id`, which is `ON DELETE SET NULL`.
4. If a delete is blocked because a save references the row, the whole import rolls back with a message naming the row **and** the save (save id, player, slot).

**Why.** Upserting keeps ids stable, so existing saves stay valid. Deleting removed rows keeps the DB an exact mirror of the JSON. RESTRICT turns "silently corrupt a save" into "refuse loudly", and the pre-check query gives a human-readable message instead of a raw SQLState. A removed choice only loses a journal label, which is harmless, so SET NULL is the right trade-off there. It also gives a strong viva answer on referential actions (CASCADE vs RESTRICT vs SET NULL) and transaction atomicity.

**Spec.** §4.1 (choice comment, player-table header, FK actions), §8.3.

**Superseded by P-04.** Content is schema-validated JSON loaded at startup; there are no content tables and no importer.

---

## D2 — Ending nodes must exist before Ch5 is written

**Problem.** `ending.node_id` is a non-deferrable FK to `story_node`, but the ending nodes belong to Ch5, which is written in Phase 9. Phase 1's import of `world.json` would fail.

**Decision.** Phase 1 ships a stub `ch5_finale.json` with only the RESOLVER node and the 5 resolver ending nodes (placeholder text). `END_QUICK` lives in the prologue. Ending node ids equal ending ids.

**Why.** It satisfies the FK without weakening it, and it lets the resolver be tested from Phase 2 onward.

**Spec.** §3, §4.1 (ending convention), §7 (Phase 1 stubs), §10.

---

## D3 — Prologue branches need somewhere to go before Ch1 exists

**Problem.** Phase 1 only details the merchant path, but p03's other choices would point at nodes that do not exist, so `StoryValidator` would fail (dangling edges, dead ends).

**Decision.** The non-merchant branches fold into the "Cult raid" bottleneck node. Until Phase 9 its single choice leads to the RESOLVER, which falls through to `END_WANDERER`. In Phase 9 that choice is re-pointed to Ch1.

**Why.** The content stays valid and fully playable at every phase, which is required by the phase gates, and the change in Phase 9 is a single edge.

**Spec.** §7 (Phase 1 stubs), §10.

---

## D4 — OR conditions via `grp`, for choices and endings

**Problem.** END_ASHEN needs "REP ASHEN ≥ 20 **or** FLAG gave_shard_to_cult = 1". The draft's "two ending rows" option would create 7 endings, breaking "Endings found X/6" and the `ALL_ENDINGS` trigger.

**Decision.** Add `grp SMALLINT NOT NULL DEFAULT 0` to **both** `choice_condition` and `ending_condition`. Same grp = AND, different grps = OR. One Java class, `ConditionGroup`, evaluates both and builds the lock reason text.

**Why.** This is disjunctive normal form (an OR of ANDs), which can express any boolean condition over the atomic checks while keeping the table flat. One column instead of a new table keeps the schema simple, and sharing one evaluator between choices and endings avoids duplicated logic. `ConditionGroup` stores groups in a `TreeMap<Integer, List<Condition>>` so groups are checked and described in a stable order even if grp numbers have gaps.

**Spec.** §3, §4.1, §5, §6.2, §6.3, §6.9, §8.1, §8.2.

---

## D5 — Explicit casts for PostgreSQL enum columns

**Problem.** The JDBC driver sends `setString` values as `varchar`; PostgreSQL rejects them for enum columns (`column "tier" is of type weapon_tier but expression is of type character varying`).

**Decision.** Write the cast in the SQL text: `?::weapon_tier`, `?::quest_status`, `?::node_type`, `?::item_type`.

**Why.** The alternative (`stringtype=unspecified` in the JDBC URL) hides the conversion in config. A visible cast keeps the hand-written SQL honest, which is part of what is graded.

**Spec.** §4.6.

---

## D6 — Derive atk/def/spd/maxEnergy from level

> **Revised by D28:** `max_hp` is now derived too; the "max_hp stays stored" exception below no longer applies.

**Problem.** `Stats` has atk, def, spd and maxEnergy, but `save_slot` has no columns for them, so level-up atk would be lost on load.

**Decision.** Do not store them. `Stats` derives them from `level` (atk = 6 + (level − 1), def 2, spd 5, maxEnergy 3). ~~`max_hp` stays stored.~~

**Why.** A column whose value is fully determined by another non-key column (`level → atk`) is a transitive dependency, which violates 3NF and can drift out of sync. ~~`max_hp` is kept because the table's `CHECK (hp BETWEEN 0 AND max_hp)` needs it inside the database.~~ (See D28.)

**Spec.** §4.1 (save_slot comment), §6.1, §6.10.

---

## D7 — Reseed the RNG at save time

**Problem.** `java.util.Random` cannot report its current position. Restoring `new Random(rngSeed)` with the *original* seed restarts the random sequence, so "save then keep playing" and "load then keep playing" would roll differently.

**Decision.** Before saving, `GameState.reseedForSave()` draws `fresh = rng.nextLong()`, sets `rng = new Random(fresh)` and stores `fresh` in `rng_seed`.

**Why.** After the reseed, the live game and any later load start from the same seed, so their rolls match exactly. That makes seeded combat reproducible in the demo and makes it a clean viva example of deterministic pseudo-random generators.

**Spec.** §4.1, §4.4, §6.10.

---

## D8 — No on-enter effects

**Problem.** The draft said p02 "gives the heartshard" and combat loot comes "from the encounter's node effects", but effects only exist on choices and COMBAT nodes have no choices.

**Decision.** Entering a node never changes state. Gifts are effects on a choice in that scene (p02 "Take the crystal", p08m "Take the hatchet"). Combat XP/gold come from `enemy.xp_reward` / `gold_reward`.

**Why.** It keeps one mechanism for state changes and needs no new table. It also makes "load re-enters `current_node` without re-applying effects" trivially true.

**Spec.** §6.3, §6.6, §7.1, §8.2.

---

## D9 — Flee destination

**Problem.** A successful flee had no destination node.

**Decision.** Add nullable `flee_node` to `story_node` (deferrable FK). Flee goes to `flee_node` if set, else `lose_node`, with no penalty either way. A CHECK allows `flee_node` only on COMBAT nodes.

**Why.** Writers can give fleeing its own scene when the story needs one, without being forced to for every fight. No penalty keeps fleeing distinct from losing. The CHECK keeps the column meaningful.

**Spec.** §4.1, §6.6, §8.2, §8.4.

---

## D10 — Structural edges for COMBAT and RESOLVER nodes

**Problem.** Validator check "non-ENDING nodes with no outgoing edges" would flag every COMBAT and RESOLVER node, since neither has choices.

**Decision.** `StoryGraph` builds a `Graph<String, String>` that contains choice edges **plus** COMBAT → win/lose/flee and RESOLVER → every `via_resolver` ending node. All validator checks run on this graph.

**Why.** Those are real exits from the node even though they are not choices. Modelling them as edges means BFS/DFS need no special cases, and the student-written `Graph` powers the validator directly.

**Spec.** §5, §8.4.

---

## D11 — `via_resolver` flag on endings

**Problem.** END_QUICK was "not resolver-checked, give it priority 99", but nothing in the schema said so.

**Decision.** Add `via_resolver BOOLEAN NOT NULL DEFAULT TRUE` to `ending`; END_QUICK is `false`. `EndingResolver` only considers `via_resolver = true`.

**Why.** An explicit column says what it means; "priority 99 is special" is a magic number that would need explaining in the viva.

**Spec.** §4.1, §5, §6.9, §8.1.

---

## D12 — Flag consistency warnings

**Problem.** Flags have no registry table, so the draft's "unknown flag ids" check had nothing to compare against.

**Decision.** The validator warns on flags that are **read** by a condition but never **set** by `SET_FLAG`, and on flags that are **set** but never **read**. Reserved `_` flags are excluded.

**Why.** Read-but-never-set catches typos that would permanently lock a choice. Set-but-never-read catches dead content. Both are cheap set differences — O(n) with `HashSet`. They are warnings, not errors, because a flag may be written in one chapter before the chapter that reads it exists.

**Spec.** §8.4 checks 7–8.

---

## D13 — Quest status by name in JSON, ordinal in DB

**Problem.** QUEST conditions stored a `QuestStatus` ordinal, so content authors would have to write magic numbers.

**Decision.** JSON writes `"value": "COMPLETED"`; the importer converts it to the ordinal for the INT column. `QuestStatusOrderTest` asserts that Java's `QuestStatus.values()` order equals the `quest_status` enum order in `01_schema.sql`.

**Why.** Content stays readable. The test protects against the one real risk of ordinals, which is someone reordering one enum but not the other.

**Spec.** §3, §6.2, §8.2, §10.

**Superseded by P-04.** Quest status stays a name in JSON; there is no DB column for it any more.

---

## D14 — Quest transitions never crash play

**Problem.** Initial quest states and invalid transitions (e.g. `START_QUEST` on a LOCKED quest) were undefined.

**Decision.** New game: quests with no prerequisites start AVAILABLE, others LOCKED. Valid transitions are START (AVAILABLE → ACTIVE), COMPLETE (ACTIVE → COMPLETED), FAIL (ACTIVE → FAILED). The validator catches what it can find statically. At runtime, an invalid transition logs a warning and does nothing; it never throws.

**Why.** Content mistakes should be caught at import time, where they are cheap to fix. If one slips through, a player mid-demo should see a slightly odd quest log, not a 500 error.

**Spec.** §6.5, §8.4 check 10.

---

## D15 — SILVER_TONGUE counter as a reserved flag

**Problem.** "Pass 3 reputation/affinity-locked choices" needs a counter that survives save/load, but no table held it.

**Decision.** Store it in the flag map as `_locked_passed`. Keys starting with `_` are reserved for the engine; content may read them but `SET_FLAG` on them is a validator error.

**Why.** It reuses `save_flag` with no schema change. The `_` prefix makes engine-owned state visible and protected.

**Spec.** §4.1, §6.1, §6.3, §6.8, §8.4 check 9.

---

## D16 — Lock each GameState per request

> **Refined by D22:** the lock is taken inside `GameEngine`'s public methods, not in the controllers.

**Problem.** `ConcurrentHashMap` makes the session map thread-safe but not the `GameState` inside it. Jetty serves requests on a thread pool, so two quick clicks could mutate one state at the same time.

**Decision.** Every request for a state runs inside `synchronized (state) { … }`.

**Why.** Per-session locking serialises one player's requests without blocking other players. It is the simplest correct choice and a good viva example of the difference between a thread-safe collection and thread-safe contents.

**Spec.** §2, §5, §6.3.

---

## D17 — Autosave when the chapter changes

**Problem.** "Autosave at each chapter's first node" did not say how the engine recognises that node.

**Decision.** `enterNode` autosaves to slot 0 whenever the new node's `chapter` differs from the previous node's.

**Why.** It uses data the node already has (`story_node.chapter`), so no extra "is first node" marker needs to be authored or kept in sync.

**Spec.** §6.3, §6.10.

---

## D18 — GameView grows with each phase

**Problem.** The sample GameView had no session id, inventory, quests, reputation or journal, but the UI panels need them.

**Decision.** Add `sessionId` now (returned in the body of `/api/game/new`) and add `inventory`, `quests`, `reputation`/`affinity`, `journal` and `offerReload` in the phase that introduces each panel.

**Why.** It follows the phase gates (no unused fields early) while keeping React a pure renderer: every value is computed in Java.

**Spec.** §9.1, §9.2.

---

## D19 — SPIRE region and location accents

**Problem.** p10m expected a "region tint change" but every prologue location is VILLAGE, and the Crystal Spire had no region.

**Decision.** Add `'SPIRE'` to the `location.region` CHECK and use it for the Crystal Spire (with its own theme). Send `location` in `GameView`; one-off scenes such as `eldmere_burning` are styled with `body[data-location]`.

**Why.** The Spire is a distinct place in the story and deserves its own theme. Location accents handle single scenes without inventing extra regions. Both are presentation only.

**Spec.** §4.1, §7, §7.1, §9.2, §9.3.

---

## D20 — Immutable enemy templates, fresh enemies per fight

**Problem.** If `ContentRepository` loaded `Enemy` objects (which have mutable hp and AI state) and every session used them, one player's fight would damage another player's enemy.

**Decision.** `ContentRepository.loadEnemies()` returns immutable `EnemyTemplate` records. `CombatEngine.start` creates new `Enemy` objects (with their own `EnemyAI` instances and Demo Mode scaling) for each fight. General rule: content loaded at startup is immutable and shared; anything that changes during play is per session or per fight.

**Why.** Immutable objects are safe to share between threads without locks. Separating "what an enemy is" (template) from "this enemy in this fight" (instance) is the flyweight idea (shared intrinsic data, per-use extrinsic state) and a clear viva example of aliasing bugs and how to prevent them.

**Spec.** §2, §3, §5, §6.6.

---

## D21 — CombatOutcome, with routing in GameEngine

**Problem.** `CombatEngine` had no defined way to report the result of a round, and it was unclear who moves the player to the win/lose/flee node. If `CombatController` called `CombatEngine` directly, routing and locking would be split across two places.

**Decision.** `CombatEngine.act(s, action)` returns `CombatOutcome { ONGOING, WON, LOST, FLED }`. `GameEngine.combatAction(s, action)` locks the state, checks the action is allowed, calls `act`, and routes to `win_node`, `lose_node`, or `flee_node`/`lose_node`. `CombatController` calls only `GameEngine`.

**Why.** `CombatEngine` stays a pure combat-rules class with no knowledge of the story graph, which makes it easy to unit test. The story navigation stays in one place (`GameEngine`), and the enum makes the four outcomes explicit in a switch expression the compiler checks for completeness.

**Spec.** §2, §3, §6.6.

---

## D22 — Save and load go through GameEngine; repository returns raw rows

> **Revised by P-05:** the repository now reads and writes a `SaveSnapshot` JSON document instead of raw rows, behind the `SaveStore` port (P-02). Save and load still go through `GameEngine`.

**Problem.** If `SaveController` called `SaveRepository` directly, it would have to handle the lock, the D7 reseed, the in-combat check and rebuilding listeners on load — game logic in a controller. Also, to rebuild items from ids, `SaveRepository` would need the content catalog, mixing persistence with content.

**Decision.** `SaveController` calls `GameEngine.save(s, slot)` and `GameEngine.load(playerId, slot)` (plus thin `listSaves`/`deleteSave`). `GameEngine` takes the lock, rejects saves outside STORY mode, reseeds, and on load rebuilds the `EventBus` and listeners. `SaveRepository.load` returns a `SaveData` record of raw rows (ids and numbers only); `GameEngine` turns ids into objects using the in-memory catalog.

**Why.** Controllers stay thin (HTTP in, JSON out). The repository only does SQL, so it can be tested with a database and nothing else. Raw rows were chosen over passing a catalog in because it keeps the dependency one-way: `engine` depends on `persistence`, never the reverse.

**Spec.** §2, §3, §4.4, §6.10, §9.1.

---

## D23 — One AchievementManager per session, shared static rules

**Problem.** A single global `AchievementManager` would need one unlocked-set per player inside it, and would be called from many request threads at once.

**Decision.** `GameEngine` builds an `AchievementManager` per session (new game or load), seeded with that player's unlocked ids from the DB, and subscribes it to the session's `EventBus`. The rules map is `static final` and shared.

**Why.** Per-session state lives with the session and is protected by the session's lock (D16), so no extra synchronization is needed. Rules are stateless, so sharing them is safe and avoids rebuilding the map for every session.

**Spec.** §5, §6.8, §6.10.

---

## D24 — Autosave order: reseed live, then copy, then submit

**Problem.** The order of reseeding and copying matters. Copying first and reseeding the copy would leave the live game on the old seed, so "keep playing" and "load the autosave" would roll differently (the bug D7 exists to prevent). Copying the `EventBus` or combat state into the snapshot would share live objects with the background thread.

**Decision.** On the request thread, under `synchronized (state)`: (1) `reseedForSave()` on the **live** state, (2) `deepCopy()`, (3) submit the copy. `deepCopy` excludes `bus`, `combat` and `pendingToasts`.

**Why.** After step 1 the live state and the snapshot share the same fresh seed. Doing it under the lock means no other request can change the state between reseeding and copying. Excluding listener and UI fields keeps the snapshot plain data that the background thread can write without touching anything the request threads use.

**Spec.** §5, §6.3, §6.10.

---

## D25 — Save Demo Mode with the slot

**Problem.** Demo Mode (enemy hp/atk ×0.6) was part of `GameState` but not of the save, so loading a demo save would silently switch to normal difficulty.

**Decision.** Add `demo_mode BOOLEAN NOT NULL DEFAULT FALSE` to `save_slot`; restore it on load.

**Why.** The prepared demo saves (spec §11) must load in Demo Mode to be demo-safe.

**Spec.** §4.1, §4.4, §6.10.

---

## D26 — Thread-safe SqlLog

**Problem.** `SqlLog` keeps the last 5 SQL statements for the Inspector, and it is written by every repository on every request thread plus the autosave thread. A plain `ArrayDeque` is not thread-safe.

**Decision.** A bounded `ArrayDeque<String>` (5 entries) behind `synchronized` methods (`record` adds and trims, `lastFive` returns a copy).

**Why.** `ConcurrentLinkedDeque` was considered, but "add, then trim to 5" is two steps and its `size()` is O(n) and not atomic, so the bound could be broken under contention. A small `synchronized` block keeps both steps atomic; the lock is held for microseconds, so contention does not matter.

**Spec.** §5.

**Superseded by P-02.** `SqlLog` and the Inspector's SQL panel were deleted in the move to Spring Boot.

---

## D27 — ActionNotAllowedException (409) and SaveNotFoundException (404)

**Problem.** Only a vague "combat-save conflict → 409" existed. Many requests are well-formed but not allowed in the current mode, and loading an empty slot had no defined response.

**Decision.** `ActionNotAllowedException` → 409 for any action the current mode forbids (save outside STORY, choice during combat, combat action outside combat, forbidden FLEE, SKILL with no energy, unusable ITEM). `SaveNotFoundException` → 404 for loading or deleting an empty slot.

**Why.** 409 Conflict means "valid request, wrong state of the resource", which matches exactly. 404 is the standard answer for a missing resource. Named exceptions keep `ErrorHandler` a simple lookup table.

**Spec.** §3, §4.4, §6.3, §6.6, §6.10, §9.1.

---

## D28 — Derive max_hp too; CHECK uses the formula

**Problem.** D6 kept `max_hp` only so `CHECK (hp BETWEEN 0 AND max_hp)` could work, which left one non-3NF column (`level → max_hp`).

**Decision.** Remove `max_hp`. The CHECK becomes `CHECK (hp BETWEEN 0 AND 40 + 5 * (level - 1))`. `Stats.maxHp()` uses the same formula. `MaxHpFormulaTest` reads the expression from `01_schema.sql` and asserts it equals `Stats.maxHp()` for a range of levels.

**Why.** A CHECK can compute an expression from other columns in the same row, so the database still rejects impossible hp without storing derived data. Every table is now in 3NF with no exceptions. Keeping the formula in two places is the risk, and the test removes it.

**Spec.** §3, §4.1, §4.4, §6.1, §10.

**Superseded by P-05.** Saves are JSONB snapshots; there is no `hp` column or CHECK. Max HP is still derived, never stored.

---

## D29 — xp is progress within the current level

**Problem.** If `xp` were a running total, `level` would be computable from it (`xp → level`), which would make storing `level` a transitive dependency.

**Decision.** `xp` is progress inside the current level. On level-up it resets to the leftover (`xp − xpToNext`, with `xpToNext = 100 * level`), repeating if several levels are gained at once.

**Why.** Under this rule two saves can have the same `xp` and different levels, so there is no functional dependency between them and both columns belong in the table. It also matches the HUD, which shows "xp / xpToNext" for the current level.

**Spec.** §4.1, §6.1, §6.2.

---

## D30 — Encapsulated GameState and CombatState

**Problem.** The spec's `GameState` had package-default fields that other packages would write directly (`s.flags.merge(...)`, `s.currentNode = ...`), so any class could break invariants such as the −100…100 reputation clamp.

**Decision.** All fields are private. Other packages use intent methods (`setFlag`, `addReputation` with clamping, `setQuestStatus`, `addToast`, …) and unmodifiable views (`flagsView()`, `reputationView()`, `affinityView()`, `questsView()`, `historyOldestFirst()`), all named consistently with a `View` suffix except the history copy. Raw mutable collections are package-private: inside `engine` for `GameState` and inside `combat` for `CombatState`.

**Why.** Invariants are enforced in one place, and the class is a clean example of encapsulation for Java-2. Views are read-only, so persistence and DTO builders can read state without being able to change it.

**Spec.** §2, §4.4, §5, §6.1, §6.3.

---

## D31 — Diagram and schema clean-ups

**Problem.** Three small gaps: the class diagram's `Graph` adjacency type had an extra closing bracket; `ContentRepository` had no way to load locations and NPCs (both needed for regions and affinity); the `flee_node` CHECK was written in a less direct form.

**Decision.** Fix the type to `HashMap‹V, List‹Edge‹V, E›››`. Add `loadLocations()` and `loadNpcs()` to `ContentRepository`. Write the constraint as `CHECK (flee_node IS NULL OR type = 'COMBAT')`.

**Why.** The new form reads as the rule it enforces ("a flee node is only allowed on a COMBAT node"); it is logically the same as the earlier one.

**Spec.** §4.1; class diagram §2 and §6.

---

## D32 — Energy can never be negative in a save

**Problem.** `save_slot.energy` had no constraint, so a bug in SKILL cost handling could store negative energy and the DB would accept it.

**Decision.** Add `CHECK (energy >= 0)` to `save_slot`. (Also in this round: `GameState.flags()` renamed to `flagsView()` for consistency with the other unmodifiable views — see D30.)

**Why.** Every other numeric column in `save_slot` already has a lower bound; energy was the odd one out. The upper bound (`maxEnergy`, a constant 3) is enforced in Java because it may change with level later.

**Spec.** §4.1.

---

## Smaller decisions

| # | Decision | Why | Spec |
|---|---|---|---|
| S1 | Combat speed ties: player acts first, then enemies by encounter index. SKILL ×1.8 applies to `base`, before the random roll and crit. | Deterministic order is needed for seeded tests; applying the multiplier to `base` keeps the formula a single readable line. | §6.6 |
| S2 | Energy refills to max at the start of every combat; consumables also restore it. | Each fight starts fair; consumables still matter mid-fight. | §6.6 |
| S3 | Boss loss → Java sets `offerReload: true`; the UI shows a reload button that calls the normal load endpoint. | Java decides when to offer it; React only renders. | §6.6, §9.2 |
| S4 | `POST /api/profiles` with an existing username returns that player. | There are no passwords, so the username acts as a simple login. Avoids a confusing 409 on the title screen. | §9.1 |
| S5 | Vite dev server proxies `/api` to `localhost:7070`. | One origin in the browser, so no CORS configuration and the `X-Session` header passes untouched. | §2, §3 **Superseded by P-02** (API on :8080). |
| S6 | `SaveRepositoryIT` runs on a separate `shardbound_test` database via the failsafe plugin (`mvnw verify`). | Integration tests must not touch real saves; `mvnw test` stays fast and DB-free. | §3, §4.4, §10 **Superseded by P-08** (Testcontainers). |
| S7 | `insertHistory` walks the history stack with `descendingIterator()` so `step_no` 1 is the oldest step. | `ArrayDeque` used as a stack iterates newest-first; without this the journal loads reversed. | §4.1, §4.4 |
| S8 | Phase 2 cannot go green until the student-written `Graph.bfs/dfs/shortestPath` exists, because `StoryValidator` uses it. | Makes the dependency explicit in the phase plan. | §10 |
| S9 | Language level Java 21 via `maven.compiler.release=21`; the installed JDK 25 is fine. | `--release 21` rejects any API added after 21, so the code runs on any Java 21 machine. | §2 **Superseded by P-02** (Java 25). |

---

## Implementation choices I1–I12 (build of Phases 1–8, pending review)

Made while writing the code, where the spec was silent. Listed in spec §12. **Not yet approved by the student** — each is easy to change.

| # | Choice | Why |
|---|---|---|
| I1 | `content/` package with one raw shape (`RawContent`) for both the JSON and the DB path | One validator and one assembler serve the importer, the server and the tests. Testable without a database. |
| I2 | Offline mode (`ConsoleRunner --json`, `ContentImporter --dry-run`, unit tests from JSON) | PostgreSQL was not installed yet; the engine could still be built and verified. Offline mode refuses saves rather than faking them. **Superseded by P-04** (`ConsoleRunner` and `ContentImporter` deleted). |
| I3 | Event types `ITEM_LOST`, `FLAG_SET`, `GOLD_CHANGED`, `HEALED`, `XP_GAINED` | Spec §6.2 says every effect publishes an event, but §6.8's list had no type for these effects. |
| I4 | Helper types (`Rewards`, `Encounter`, `DamageRoll`, `Hit`, `ActionType`, `ItemType`, `RepositoryException`, `Sessions`, request/response records) | Keep methods small (CLAUDE.md rule 12) and avoid duplicating reward logic in effects, quests and combat. |
| I5 | Full bag → item left behind with a toast, no exception | An exception halfway through a choice's effect list would leave the state half-changed. |
| I6 | Enemies crit at 5% | The damage formula needs a crit chance; enemies have no weapon to take it from. |
| I7 | BossAI phase 2 = heavy attack every 2nd turn (phase 1: every 3rd) | The spec names a phase 2 but not what it changes. |
| I8 | Level-up also heals +5 | Otherwise the new max hp would show as missing health. |
| I9 | Extra CHECK constraints (non-negative values) | Cheap integrity, consistent with the existing constraints. |
| I10 | `?playerId=` accepted when no session; load without a session creates one | The title screen's "Load" happens before any game session exists. |
| I11 | GameView ships all panel fields now | All phases' engine code was written in one pass. |
| I12 | `compile exec:java`; default main class as a property | Found while running the tools: plugin configuration silently overrode `-Dexec.mainClass`. **Superseded by P-02** (`exec:java` removed; `spring-boot:run`). |

---

## v2 design decisions (V-01 … V-35)

Recorded with the v2 design (2026-11-18). The specification is `docs/V2_SPEC.md`; each row says **why**.

| # | Decision | Why |
|---|---|---|
| V-01 | Names: **Lanterns**, **Shards**, **Lost Shards**, **"FOE VANQUISHED"**, **Lantern Draught** | Souls-like mood without the identifiable Elden Ring names (Sites of Grace, runes, "ENEMY FELLED"). Your choice. |
| V-02 | v1 tagged `v1-archive`; all v2 work on branch `v2` | The graded semester version stays reproducible while v2 changes almost every layer. **Superseded by P-26** (archive tag, no PostgreSQL run) and P-17 (`main` is the trunk). |
| V-03 | Grid-locked 4-direction movement, one tile per step | Server validation becomes simple and exact (adjacent tile, walkable bit, rate). Free pixel movement would need physics on the server. **Superseded by P-06.** |
| V-04 | WebSocket only for the world (movement, interact, pushes); REST for everything else | Steps are frequent and small; battles, dialogue and menus are request/response and easier to test over REST. **Superseded by P-06.** |
| V-05 | WS handshake checks the `sb_session` cookie and `Origin` | Browsers send cookies with cross-site WS upgrades; the Origin check is the CSRF defence for WebSockets. **Superseded by P-06.** |
| V-06 | WS frames ≤ 1 KB, ≤ 20 msgs/s, one socket per game | Bounds memory and CPU per client; stops a client from spamming steps faster than the step rate limiter can reject them cheaply. **Superseded by P-06.** |
| V-07 | Reconnect = full snapshot from server state | The server is the truth, so resuming is "send everything"; no client-side replay logic to get wrong. **Superseded by P-06.** |
| V-08 | WS messages use the same `synchronized (state)` lock as REST (D16 extended) | A move arriving while a REST request ends a battle could otherwise interleave on one GameState. **Superseded by P-06** (no WebSocket; the D16 lock still covers every REST handler). |
| V-09 | Creeps are static with an aggro radius; patrol is cosmetic | The server never simulates moving creeps, so encounters are deterministic and testable. |
| V-10 | All constants in `content/balance.json` → `curve_point`; soft caps as piecewise-linear `Curve`s (✍ `valueAt`) | Balance is data (rule 4), tunable without code; TreeMap interpolation is a good DS exercise. **Superseded by P-04 and P-01** (constants stay in `balance.json`, no `curve_point` table, no ✍ marker). |
| V-11 | `cost(L) = round(100·L^1.5)`, `creepShards(lvl) = round(35·lvl^1.45)`, boss ×5 | Grid-searched against the §10.2 progression table: every boss target hit exactly, first level-up after fight 3. |
| V-12 | Defence as a ratio `100 / (100 + DEF)` instead of v1's subtraction | Subtraction collapses to the 1-damage floor at larger numbers; a ratio keeps every hit meaningful. |
| V-13 | ATTACK = Strike / Guard; Guard halves damage, quarters a telegraphed heavy | The required menu has no DEFEND; telegraphs need a counter that rewards reading the text box. |
| V-14 | FP persists between battles; heals come from refillable Lantern Draughts | The Souls-like resource loop: resting matters. v1's "refill energy every fight" (S2) does not apply in v2. |
| V-15 | `save_slot.hp` upper bound enforced in Java, not by CHECK | Max HP now comes from a curve stored in rows; a CHECK can only read its own row. D28's idea holds where possible. **Superseded by P-05.** |
| V-16 | Weapon upgrades only at Smith NPCs | Gives the hub areas a purpose and makes fast travel useful. |
| V-17 | Dijkstra (✍) drives fast travel, quest next hop, the bot and region preloading | One algorithm with four visible uses in the viva. **Superseded by P-01** (Dijkstra still drives these features; the ✍ framing is gone). |
| V-18 | Flyway migrations replace `database/0x_*.sql` | Versioned, repeatable schema changes; the SQL is still hand-written. |
| V-19 | DB roles: owner (migrations), app (DML only), telemetry read-only | Least privilege: a SQL-injection bug in the app could not drop tables. |
| V-20 | Argon2id m=19 MiB, t=2, p=1 (Bouncy Castle) | OWASP's minimum Argon2id setting; pure Java, no native library in Docker. |
| V-21 | Sign-up and forgot-password always return the same 202; an existing email gets an "account exists" mail | Prevents account enumeration through either form. |
| V-22 | Random 32-byte session token in an HttpOnly/Secure/SameSite=Strict cookie; DB stores SHA-256; Origin check on writes | A stolen DB backup cannot be used to log in; JS cannot read the cookie; SameSite + Origin stop CSRF. **Superseded by P-11** (SameSite=Lax + CSRF token). |
| V-23 | Transactional outbox for email | The email row commits with the account/token change or not at all; sending is retried outside the request. |
| V-24 | Eclipse Angus Mail for SMTP (**approved**) | SMTP with STARTTLS and auth by hand is error-prone; Angus is the reference Jakarta Mail implementation. |
| V-25 | Guests are real accounts with `is_guest`; upgrade keeps `account_id` | Progress survives the upgrade without copying saves. |
| V-26 | Telemetry: BRIN on `at` **and** B-tree on `(run_id, at)` | BRIN: append-only rows arrive in time order, so a tiny block-range index serves time-window dashboard scans. B-tree: window functions over one run (`LEAD … PARTITION BY run_id ORDER BY at`) need that run's rows in order, which BRIN cannot provide. |
| V-27 | Telemetry keyed by an anonymous `run_id`, with an opt-out setting | Balancing needs runs, not people; less personal data to protect on a public deploy. Your choice. |
| V-28 | Dev chapter select: route registered only in dev, server rejects it otherwise, frontend code dead-code-eliminated, CI greps the prod bundle | Defence in depth: a presentation tool must never ship. |
| V-29 | CI bundle-size budget from V3; Vitest for the event store and mappers | Budgets regress silently without a gate; the mappers are the only frontend logic worth unit-testing. |
| V-30 | Rule 8 adds `Graph.dijkstra`, `Curve.valueAt`, `SlidingWindowRateLimiter` | Three more DS/algorithm pieces you write and explain yourself (8 in total). **Superseded by P-01.** |
| V-31 | `map_object.props` as JSONB | Each object type has different properties; the queried fields (type, area, x, y) are real columns. **Superseded by P-04** (maps are loaded from `.tmj` at startup; no `map_object` table). |
| V-32 | Fonts self-hosted (not Google Fonts CDN) | No third-party requests (privacy page stays short), and a CSP of `font-src 'self'`. |
| V-33 | Loose, configurable rate limits: login 100 per IP / 15 min, 5 failures per email / 15 min, guests 30 per IP / hour; every value in config, none hardcoded | Whole college campuses and mobile carriers share one public IP (NAT), so tight per-IP limits would lock out real players. The per-email limit protects each account, and Turnstile is the main bot defence. Config lets limits be tuned after launch without a release. |
| V-34 | Cloudflare's official Turnstile test keys in dev and CI; the playtest bot runs only in dev mode; no Turnstile or rate-limit bypass in production builds | Dev and CI exercise the real verification code; a bypass switch is exactly what leaks into production. |
| V-35 | §10.2 adds a robustness scenario (one death per boss, half the Lost Shards never recovered); the model script lives in `tools/balance/`; V10 requires every boss beatable (≥ 40 %) at target level − 2 with the weapon one upgrade lower | The progression must hold for imperfect players and be re-runnable from the repo. The simulator check covers the harsh cases the economy model cannot (dying with a full purse, being under-levelled). |

---

## Platform reset decisions (P-01 … P-50)

Recorded with Phase P0 (2026-11-18). Shardbound became a public game and portfolio piece instead of a course project. These entries win over every older entry; superseded entries above say so. The specification is `docs/V2_SPEC.md`.

---

## P-01 — Product goal replaces the course goal

**Problem.** The rules existed to show concepts to an examiner: hand-implemented algorithm methods with a study guide, a syllabus map, mandated data structures and complexity comments on every method. None of that serves players or code reviewers.

**Decision.** The goal is a public, well-engineered game. The hand-implemented (✍) method rule, `STUDY_GUIDE.md`, `SYLLABUS_MAPPING.md`, the exam deadline and every exam-only rule are removed (the two docs are preserved in tag `v1-archive`). The algorithms stay where the game needs them.

**Why.** The audience is players and reviewers.

**Supersedes.** V-10 (✍ marker), V-17 (framing), V-30; CLAUDE.md rule 8.

**Spec.** V2_SPEC header, §1, §5, §13.

---

## P-02 — Spring Boot 4 on Java 25; framework-free engine

**Problem.** Javalin and hand-built plumbing (`AppConfig`, `Database`, `Migrations`, `exec:java` tools) were chosen to keep wiring visible, not because they fit a public service best. Java 21 was the course level.

**Decision.** Spring Boot 4.1 on Java 25 (web, JDBC, Flyway, Actuator, validation; Security from V2a). The engine packages (`engine`, `combat`, `quest`, `rules`, `story`, `item`, `model`, `ds`, `event`, `achievement`, `content`, `exception`, `save`, `view`) stay free of Spring, `jakarta.*` and `java.sql`, enforced by ArchUnit. Persistence is reached through ports (`SaveStore`, `MetaStore`, `RunStore`). The API listens on :8080. `SqlLog`, the SQL panel, `ConsoleRunner`, `App` and the `config` plumbing are deleted.

**Why.** Spring Boot is the industry default with a maintained ecosystem (security, observability, testing). A framework-free engine stays fast to test and portable.

**Supersedes.** D26, S5, S9, I12; the Javalin choice in the old TECH_STACK.

**Spec.** §2, §3.

---

## P-03 — JdbcClient + hand-written SQL, Flyway via Spring, no JPA

**Problem.** Raw JDBC needs boilerplate for connections, mapping and transactions; an ORM would hide the SQL.

**Decision.** Repositories use Spring's `JdbcClient` with hand-written SQL. Flyway runs through Spring at startup. Multi-row writes use `@Transactional`. No JPA, Hibernate or other ORM. Merged migrations are never edited.

**Why.** Explicit, reviewable SQL without JDBC plumbing.

**Spec.** §2, §6.

---

## P-04 — Content is schema-validated JSON loaded at startup

**Problem.** Content was imported into PostgreSQL by `ContentImporter`, then loaded back into memory. The DB copy could drift from the repo, re-imports could collide with saves (D1), and content changes needed a separate import step.

**Decision.** Content stays JSON (and `.tmj` maps) under `content/`, validated against JSON Schemas (2020-12) in `content/schema/` and loaded at startup by `ContentLoader`. Any error stops startup. There are no content tables, no importer and no `MapImporter`. The `all_endings` check moves into `AchievementManager`; `fn_best_weapon` is dropped. Unknown content ids in a save give `SAVE_INCOMPATIBLE` (409).

**Why.** Content ships with code review, there is no DB/repo drift, and failures surface at startup.

**Supersedes.** D1, D13, I2, V-10 (`curve_point`), V-31.

**Spec.** §2, §6, §9.

---

## P-05 — Saves are JSONB snapshots with versioned upgrades

**Problem.** Normalised save tables (a dozen `save_*` children) made every save a multi-table delete-and-insert and every format change a schema migration. The v1 → v2 node conversion (`SaveMigrator`, `content/migration/v1_nodes.json`) existed for saves that never existed in production.

**Decision.** One `save_slot` row per slot with `data JSONB` (a `SaveSnapshot`), `save_version`, and summary columns `level`, `area_id`, `playtime_seconds`, written in one UPSERT. `SaveUpgrader` applies a `SortedMap<Integer, SaveUpgrade>` of JSON transforms on load and writes the result back in the same transaction. The v1 → v2 conversion is dropped.

**Why.** A save is a document. No v1 saves exist in production.

**Supersedes.** D28, V-15; revises D22.

**Spec.** §4.12, §6.

---

## P-06 — Free movement, REST event validation, no WebSocket

**Problem.** Grid-locked movement over a WebSocket feels stiff on phones, needs a socket protocol (handshake, frame limits, reconnect snapshots) and a host that supports WebSockets, and is harder to test.

**Decision.** The client moves freely and smoothly. The server validates three REST events (`/api/world/exit`, `/interact`, `/encounter`) that carry the client position: walkable tile, BFS reachability from the last validated position, speed check (P-19), and target in range. Saves store the last validated position. The per-game lock is unchanged. No WebSocket.

**Why.** Smooth mobile movement; simpler hosting and testing; the server still owns every outcome.

**Supersedes.** V-03, V-04, V-05, V-06, V-07, V-08.

**Revised by** P-51 (tile-step movement replaces free movement; the REST events and checks stay).

**Spec.** §2, §4.11, §7.

---

## P-07 — OpenAPI contract and generated TypeScript client

**Problem.** Hand-written TypeScript view types drift from the Java records, and errors were an ad-hoc `{error, message}` shape.

**Decision.** springdoc generates `api/openapi.json` (committed; CI fails on drift). openapi-typescript generates the client types; openapi-fetch is the client. Errors are RFC 9457 `ProblemDetail` with a stable `code`.

**Why.** One contract, no drifting hand-written types, a standard error format.

**Spec.** §7, §8.

---

## P-08 — Testcontainers for all integration tests

**Problem.** Integration tests needed a locally installed PostgreSQL with a hand-made `shardbound_test` database, and CI used a different setup.

**Decision.** Every IT runs against `postgres:17-alpine` through Testcontainers, one shared container per run (`@ServiceConnection`). `shardbound_test` and the `TEST_DB_*` settings go away.

**Why.** The same Postgres locally and in CI.

**Supersedes.** S6.

**Spec.** §4.15.

---

## P-09 — Docker Compose and one-command dev

**Problem.** Setup meant installing PostgreSQL, creating databases, copying `db.properties` and starting two processes by hand.

**Decision.** Root `docker-compose.yml` runs `postgres:17` and `mailpit` (bound to localhost). `npm run dev` at the repo root starts compose, the API (`dev` profile) and Vite. The backend runs on the host for hot reload; there is no nginx or backend container in dev.

**Why.** Fast onboarding and hot reload on the host.

**Spec.** §4.15.

---

## P-10 — Mobile-first controls and layout

**Problem.** The UI targeted desktop browsers with keyboard input.

**Decision.** Touch D-pad, tap to interact, landscape responsive HUD, battle touch targets ≥ 48 px. Keyboard and gamepad stay supported.

**Superseded by** P-40 (input-aware layouts; portrait is the main phone layout).

**Why.** Most target players are on phones.

**Spec.** §4.16.

---

## P-11 — Guest + Google at alpha, email in V2b; Spring Security; SameSite=Lax + CSRF

**Problem.** Email accounts are only safe in public once verification and password reset exist, which pushed accounts far ahead of the playable slice. A `SameSite=Strict` cookie breaks the OAuth callback.

**Decision.** V2a ships guest accounts and Google sign-in (OIDC) with guest → Google upgrade; email + password follows in V2b. Spring Security handles sessions, CSRF and headers. The session cookie is `SameSite=Lax`, with a CSRF token and an Origin check. Google is linked to an existing account only when both Google's and the account's email are verified. Argon2id comes from Spring Security's encoder (Bouncy Castle). Limits are `shardbound.security.*` properties.

**Why.** No public email accounts without reset and verification. Strict breaks the cross-site callback navigation. Verified-only linking prevents account pre-hijacking.

**Supersedes.** V-22 (cookie attribute).

**Spec.** §4.10.

---

## P-12 — Sentry, JSON logs, Actuator

**Problem.** A public game needs to see player-facing errors; console logs on one machine are not enough.

**Decision.** Sentry in frontend and backend without PII, structured JSON logs, Actuator health and metrics (V2c).

**Why.** Find player-facing errors.

**Spec.** §4.17.

---

## P-13 — Backups, Dependabot, secret scanning, preview deploys, release-please

**Problem.** There was no release process, dependency updating, secret scanning or backup plan.

**Decision.** Managed Postgres with PITR or daily backups and a tested restore; Dependabot (maven, npm, actions, docker); gitleaks; preview deploys per PR; release-please for semver tags and `CHANGELOG.md`.

**Why.** Standard release hygiene.

**Spec.** §4.15.

---

## P-14 — Installable PWA; `/api` never cached

**Problem.** Repeat loads re-download large region atlases, and mobile players expect a home-screen app.

**Decision.** Manifest + service worker: precached shell, versioned region atlases cache-first, `/api` never cached.

**Why.** Home-screen install and fast repeat loads, without ever serving stale game state.

**Spec.** §4.16.

---

## P-15 — Privacy policy, data export, account deletion

**Problem.** A public game that stores accounts needs a privacy baseline.

**Decision.** Privacy policy and terms pages, JSON data export, and account deletion with anonymised leaderboard rows, from V2a. India's DPDP Act and the GDPR are the reference.

**Why.** Legal and trust baseline.

**Spec.** §4.18.

---

## P-16 — Hosting shape

**Problem.** The host was to be chosen at V12 and had to support WebSockets; the audience's location was not considered.

**Decision.** Backend in Mumbai or Singapore, frontend on a CDN that proxies `/api`, managed Postgres. The provider is chosen at deploy time (V2c).

**Why.** Low latency for the audience; one origin keeps cookies first-party.

**Spec.** §4.15.

---

## P-17 — Phase order and feedback button

**Problem.** Accounts and ops came before the game was playable, so nothing public could ship until late.

**Decision.** P0 → V3 → V4 → V5 → V7 → V2a → V2c → ALPHA → V6, V8, V2b, V9, V10, V11a–d, V12. ALPHA adds an in-game feedback button. `main` is the trunk; each phase is one branch and one PR into `main`.

**Why.** A public vertical slice early.

**Supersedes.** V-02 (branching); the old V2_SPEC §12 order.

**Spec.** §12.

**Amended by P-27** (Whispering Wood placed after V6). **Superseded by P-66** (no public alpha; one launch as 1.0).

---

## P-18 — Engineering standards replace course rules

**Problem.** CLAUDE.md's rules were written for grading (visible wiring, mandated structures, complexity comments everywhere).

**Decision.** Twelve engineering rules: server-authoritative rules, framework-free engine, JdbcClient + hand-written SQL, transactions, schema-validated content, production-quality code, behaviour tests, OWASP ASVS L1 and no secrets, Conventional Commits and one PR per phase, libraries chosen on merit, mobile-first UI, and a phase report.

**Why.** These are the standards a reviewer of a public project expects.

**Spec.** CLAUDE.md.

---

## P-19 — Speed check on movement events

**Problem.** Reachability alone lets a client teleport to any reachable tile.

**Decision.** A world event is rejected unless the BFS path from the last validated position is ≤ `speed × elapsed × 1.25 + slack` tiles.

**Why.** Blocks teleporting to reachable tiles while tolerating latency.

**Spec.** §4.11.

---

## P-20 — V2a split into V2a and V2c

**Problem.** V2a (accounts, security, compose, deploy) was the largest phase.

**Decision.** V2a covers accounts and privacy; V2c covers observability, deploy, PWA and version skew.

**Why.** Accounts and ops ship and get reviewed separately.

**Spec.** §12.

**Superseded by P-66:** V2c is gone; its parts are in S1 Private staging and V12.

---

## P-21 — PostgreSQL 17

**Problem.** PostgreSQL 16 was chosen for a local install.

**Decision.** PostgreSQL 17 in compose, Testcontainers and CI. Revisited at deploy time against the managed provider.

**Why.** Current managed-provider default.

**Spec.** §2, §4.15.

---

## P-22 — Durable sessions: `active_run`

**Problem.** Live runs existed only in memory, so a deploy or restart lost every player's unsaved progress.

**Decision.** `active_run(run_id, player_id, session_token_hash, version, data JSONB, updated_at)` is the source of truth; the in-memory `GameState` is a bounded Caffeine cache. Every state-changing action writes through with optimistic locking (`… WHERE version = ?`; 0 rows → `409 RUN_CONFLICT`). A cache miss reloads from the table.

**Why.** Deploys and restarts must not lose progress.

**Spec.** §4.12.

---

## P-23 — Version skew handling

**Problem.** PWA caches mean old clients meet new servers.

**Decision.** `X-API-Version` / `X-Client-Version` headers, an "Update available — reload" prompt, a backend compatible with the previous frontend for one release, and `oasdiff` in CI (V2c).

**Why.** Old cached clients must keep working or be told to reload.

**Spec.** §4.19.

---

## P-24 — Mobile performance budget

**Problem.** Desktop load budgets say nothing about frame rate or memory on phones.

**Decision.** Target a mid-range Android (~4 GB RAM, Snapdragon 6-series class): 60 fps target, 30 fps floor (p95 frame ≤ 33 ms), JS heap ≤ 100 MB and texture memory ≤ 64 MB per region. Measured with Playwright + CDP 4× CPU throttling in CI, plus one real-device check per region (from V3).

**Why.** The target device class is mid-range Android.

**Spec.** §4.13.

---

## P-25 — Licences decided before ALPHA

**Problem.** The repository goes public at ALPHA without stated terms.

**Decision.** Choose licences for code and for story/art separately before ALPHA (a launch-checklist item).

**Why.** A public repo needs clear terms for code and creative work.

**Spec.** §11, §12.

---

## P-26 — v1 archived as `v1-archive` without the PostgreSQL run

**Problem.** The planned semester tag for v1 waited on a PostgreSQL verification run that only mattered for submission.

**Decision.** Annotated tag `v1-archive` on `main` at `0f6c993` ("v1 archived; never verified on PostgreSQL"). The PostgreSQL run is skipped. The deleted study docs are preserved in the tag.

**Why.** v1 is no longer submitted.

**Supersedes.** V-02 (tag name).

**Spec.** §12, §13.

---

## P-27 — Whispering Wood is phase WW, released as Alpha 2

**Problem.** The new phase order (P-17) left Whispering Wood (5 areas, 15 fights, Thornmother) without a phase: V7 became Eldmere-only and V11a starts at Hollow Deep.

**Decision.** Add phase **WW** right after V6 and release it as **Alpha 2**. Order after ALPHA: V6 → WW → V8 → V2b → V9 → V10 → V11a Hollow Deep → V11b Valdren City → V11c Ashen Wastes → V11d Crystal Spire → V12. WW is verified by manual playtests and the §10.2 progression table; the V10 playtest bot re-checks its 18-minute budget later.

**Why.** Whispering Wood is the second region on the critical path and its boss expects a +3 weapon (§10.2), so it belongs right after gear (V6). Shipping it as Alpha 2 gives alpha players new content early, before the bot exists.

**Supersedes.** The open note under V2_SPEC §12; amends P-17.

**Spec.** §12.

**Superseded by P-66:** no Alpha 2; WW follows PLAYTEST 1 in the new order.

---

## P-28 — CI guards content ids against unmapped removal

**Problem.** Saves and active runs store content ids (P-05). Renaming or removing an id in content makes those saves fail with 409 `SAVE_INCOMPATIBLE` on load, which a player only discovers after a deploy.

**Decision.** From V2c, next to the `oasdiff` check, CI compares every content id with the last release's content and fails if an id is removed or renamed without a `SaveUpgrade` that maps it. The 409 on load stays as the last line of defence.

**Why.** It moves the failure from a player's load screen to the pull request that caused it, the same way `oasdiff` guards the API.

**Spec.** §4.12, §12 (V2c).

---

## P-29 — 16×16 art at an integer scale; Ninja Adventure

**Problem.** The spec fixed 32×32 tiles from Kenney or 0x72. Neither family offers animated characters, portraits or bosses at that size, and drawing them ourselves is ruled out (no AI art, no budget for an artist).

**Decision.** Tiles are 16×16, drawn at an integer scale and pixel-perfect (Phaser `pixelArt`, `roundPixels`, integer camera zoom). The family is **Ninja Adventure** by pixel-boy (CC0), chosen after comparing it with the Kenney Roguelike and Kenney Tiny families from their actual files. It is graded dark with a palette remap and a per-region colour grade. The server's tile maths reads the map's `tilewidth` instead of assuming a size.

**Why.** It is the only CC0 family with walking players and NPCs, animated monsters and bosses, and dialogue portraits: everything the world, battle and boss phases need. Its bright tone is fixable with a grade; missing animation is not.

**Supersedes.** The 32×32 / Kenney-or-0x72 rule (§1, §9, §11).

**Spec.** §1, §4.11, §9, §11.

---

## P-30 — A session is dropped only when the server says the run is gone

**Problem.** In the P0 browser test a reload once landed on the title screen although the run was intact. On a page load the client tried `GET /api/game` once and treated *any* failure as "stale session", so a reload while the API was restarting (a 500 from the dev proxy, a network error) silently threw the player out.

**Decision.** The client discards its session only on a definite `404` with code `RUN_NOT_FOUND` (renamed from `SESSION_NOT_FOUND`). Network errors, 5xx and `RUN_CONFLICT` show **"Reconnecting…"** and retry `GET /api/game` with backoff 0.5 → 1 → 2 → 4 → 8 s (capped, ±20 % jitter) until the run resumes. The player can go back to the title without losing the session. A failed action shows the same overlay and refetches the game; retrying the action itself waits for idempotency keys (P-31, V4).

**Why.** Deploys and restarts are normal for a hosted game, and P-22 already guarantees nothing is lost on the server. The client has to be as patient as the server is durable.

**Spec.** §4.12, §7.

---

## P-36 — Predictable checks gate PRs; performance runs nightly

**Problem.** CPU-throttled frame-time and heap measurements vary with runner load. As PR gates they would fail at random and teach everyone to re-run CI until it is green.

**Decision.** PR gates are the predictable checks: unit, integration and e2e functional tests, bundle and asset budgets, the production-bundle grep, the CREDITS check. The mobile performance budget (P-24) runs **nightly and before releases**. The restart-and-reload test (`resume.spec`) runs **5 cycles on PRs** and **20 nightly**.

**Why.** A gate must fail only when the code is wrong. Nightly runs still catch performance regressions within a day, and the release checklist blocks a release on them.

**Spec.** §4.13, §4.15, §12.

---

## P-31 — Idempotency keys on every state-changing action (V4)

**Problem.** After P-30 a failed action only refetches the game: the client cannot know whether the server applied it, so it cannot safely send it again.

**Decision.** From V4 the client sends an `Idempotency-Key` header (a UUID per action). The server keeps the last N processed keys and their results inside `active_run`, written in the same update as the run, and replays the stored result for a duplicate key instead of applying the action twice.

**Why.** Keeping the keys in the run row makes "applied" and "remembered" one atomic write, so a retry after any failure is safe.

**Revised by** P-56 (only the key, the request's hash and the run version are stored).

**Spec.** §4.12, §12 (V4).

---

## P-32 — No endpoint accepts a player id from the client (ALPHA)

**Problem.** Until accounts exist, save and meta endpoints accept `?playerId=`, so anyone can read or delete another player's saves.

**Decision.** The parameter disappears with V2a accounts (the player comes from the session). Before ALPHA, a contract test fails if any operation in `api/openapi.json` has a `playerId` parameter; it is an ALPHA launch-checklist item.

**Why.** Broken object-level authorization is the most common API flaw; a test against the contract stops it from coming back.

**Spec.** §7, §12 (ALPHA).

**Amended by P-66:** the contract test is a LAUNCH checklist item.

---

## P-33 — Phaser 4

**Problem.** The spec named Phaser 3.90, written before Phaser 4 was the stable line.

**Decision.** Use Phaser **4.2** for the world and battle scenes.

**Why.** It is the maintained major with the newer renderer; starting a new project on 3.x would begin on a line that only receives fixes. Risk: fewer tutorials; mitigated by a thin scene layer.

**Spec.** §2, §12 (V3).

---

## P-34 — Art manifest

**Problem.** If content and code name atlas frames directly, any art change touches dialogue files, maps, Java and TypeScript.

**Decision.** Content and code use logical sprite keys (`npc.maren`, `creep.<template>`, `portrait.<npc>.<mood>`, tile roles). One schema-validated manifest, `content/art/manifest.json`, maps keys to atlas frames, and startup validation checks every key used in content exists in it.

**Why.** A future art swap, or a recolour per region, becomes a manifest change.

**Spec.** §1, §9.

---

## P-35 — Maps ship in the frontend build

**Problem.** The client needs the same maps the server validates against. A map endpoint would add a request on every area change and keep maps out of the CDN and PWA caches.

**Decision.** `content/maps/*.tmj` is copied into the frontend build (content-hashed, CDN-cached, precached by the PWA) and loaded by the server from the same files. CI checks that the built copies match the content files. There is no map endpoint.

**Why.** Maps are static content: they belong with the other static assets, and one source keeps client and server collision identical.

**Spec.** §9.

---

## P-37 — How the prologue became a walkable world (V3)

**Problem.** The V3 plan turned the v1 story graph into dialogues with new effects (`START_ENCOUNTER`, `GOTO`, `END_GAME`, `RESOLVE_ENDING`) and the alley bodyguard into a `creep_spawn` with `/api/world/encounter`. Building it showed simpler shapes that keep the same behaviour.

**Decision.**
- Dialogue nodes keep the v1 node types (`TEXT`, `COMBAT` with `encounter`/`win`/`lose`, `RESOLVER`, `ENDING`); a choice either goes `to` a node or `end`s the dialogue. A dialogue opens on its first matching `entries` node, else `start`.
- The Hired Blade fight starts from the alley dialogue (a `COMBAT` node). `creep_spawn` objects and `/api/world/encounter` arrive with creeps in V4.
- The structural quick-route length check leaves `DialogueValidator`, because the route now crosses the world; the merchant route is played end to end by `GameEngineTest` and `QuickRouteApiIT` through world events.
- A new game opens with the start dialogue from `regions.json`; once-only triggers, `showIf`/`hideIf` and the light state are all driven by flags, and gates live only on `regions.json` edges.
- Night lighting, light radii, the player light and rain are tuned in `content/art/grades.json` (presentation); walking constants stay in `balance.json`.
- The v1 "Eldmere burning" red tint is dropped; the burning is told in the dialogue.

**Why.** The existing node engine and combat routing were proven by tests; reusing them kept every v1 path and ending reachable while the world was added around them.

**Spec.** §8, §9; V3 in §12.

---

## P-37 — How the prologue became a walkable world (V3)

**Problem.** The V3 plan turned the v1 story graph into dialogues with new effects (`START_ENCOUNTER`, `GOTO`, `END_GAME`, `RESOLVE_ENDING`) and the alley bodyguard into a `creep_spawn` with `/api/world/encounter`. Building it showed simpler shapes that keep the same behaviour.

**Decision.**
- Dialogue nodes keep the v1 node types (`TEXT`, `COMBAT` with `encounter`/`win`/`lose`, `RESOLVER`, `ENDING`); a choice either goes `to` a node or `end`s the dialogue. A dialogue opens on its first matching `entries` node, else `start`.
- The Hired Blade fight starts from the alley dialogue (a `COMBAT` node). `creep_spawn` objects and `/api/world/encounter` arrive with creeps in V4.
- The structural quick-route length check leaves `DialogueValidator`, because the route now crosses the world; the merchant route is played end to end by `GameEngineTest` and `QuickRouteApiIT` through world events.
- A new game opens with the start dialogue from `regions.json`. Once-only triggers, `showIf`/`hideIf` and the light state are all driven by flags, and gates live only on `regions.json` edges.
- Night lighting, light radii, the player light and rain are tuned in `content/art/grades.json` (presentation); walking constants stay in `balance.json`.
- The v1 "Eldmere burning" red tint is dropped; the burning is told in the dialogue.

**Why.** The existing node engine and combat routing were proven by tests; reusing them kept every v1 path and ending reachable while the world was added around them.

**Spec.** §8, §9; V3 in §12.

---

## P-38 — The nightly frame budget measures CPU time per frame

**Problem.** §4.13 measured the p95 time between frames. CI browsers have no GPU, so Chrome draws WebGL in software and paces frames at about 33 ms even without CPU throttling (measured: p95 33.4 ms unthrottled, 50 ms at 4×). The check would fail every night whatever the game costs.

**Decision.** The nightly budget is the 95th-percentile **main-thread CPU time per frame**: the sum of each frame's animation-frame callbacks (Phaser's update and render) in a DevTools trace, with 4× CPU throttling, at most 33 ms (`budgets.performance.p95FrameCpuMs` in `balance.json`). GPU cost is checked on the real device (P-24).

**Why.** It measures what the game controls on the CPU, independent of how the test machine draws pixels; the real-device check covers rendering.

**Addition.** The nightly run also fails when its p95 CPU time per frame is more than 25 % worse than the last good nightly run. Each run stores its numbers (`perf.json`) as a workflow artifact; the next run downloads the last successful run's file and compares.

**Spec.** §4.13.

---

## P-39 — A compact HUD that never covers the player

**Problem.** The v1 HUD was a full panel of numbers. In the world it covered a large part of a phone screen, often around the player.

**Decision.** A compact cluster in the top-left corner with no background panel: a level badge, a thin HP bar and a thinner energy bar below it. On phones it takes at most about 10 % of the screen height and 35 % of the width; on desktop it has a fixed maximum width. Numbers always show on desktop; on phones only while a value changes or after a tap. After a few calm seconds at full HP it fades to about 35 % opacity, and returns to full on damage, energy use, level-up, low HP or a nearby enemy (V4). Shards show bottom-right, briefly, when they change. No HUD element may cover the player or the area around them.

**Why.** The world is the game; the HUD should say what matters when it matters and otherwise get out of the way, most of all on a phone.

**Spec.** §4.16, §8.

---

## P-40 — Input-aware layouts (replaces landscape-first)

**Problem.** P-10 made the game landscape-first with a "rotate your phone" hint. Most people hold a phone upright, and a desktop player does not need touch controls.

**Decision.**
- **Keyboard and mouse:** no touch controls; the game view fills the screen.
- **Phone portrait (the main phone layout):** the game view with the HUD cluster on top; a control deck below with the D-pad, interact (A), menu and bag. While a dialogue is open its text and choices render inside the deck. No rotate hint.
- **Phone landscape:** the desktop layout plus translucent touch controls (about 40 % opacity idle, full while pressed, ≥ 48 px).
- The mode comes from input capability and the last input used (coarse pointer, touch, key presses), not from screen width, and switches live. Layouts use `dvh` units and safe-area insets.
- On touch, quick slots sit behind the bag button; the boss bar is bottom-centre (V7).

**Why.** Portrait keeps one-handed play comfortable and the controls off the world; detection by input, not width, gives a tablet with a keyboard the desktop layout and a phone in either orientation its touch controls.

**Supersedes.** P-10.

**Spec.** §4.16.

---

## P-41 — Controls screen and contextual hints

**Problem.** New players have to guess the controls, and the controls differ by device.

**Decision.** A Controls screen on the title screen and in the pause menu lists keyboard (WASD or arrows to move, E or Space to interact, Esc for the menu), the gamepad mapping and the touch controls. Contextual hints show once per profile and can be skipped, with a setting to turn them off: a movement hint at the start and an interact hint at the first object in reach. Button labels match the device in use (E, gamepad A, the tap icon). In V5 the first Lantern gets a tutorial (rest, level up) and a new lore NPC beside it who explains what has become of the world and hints at the regions ahead; that dialogue is drafted for review when V5 starts.

**Why.** Teaching in context, once, is enough; a screen to look things up covers the rest.

**Spec.** §4.9, §4.16; V5 in §12.

---

## P-42 — Minimal audio now

**Problem.** Audio was planned for V12. A silent world feels unfinished from the first minute, and the storm night depends on its rain.

**Decision.** Pulled forward from V12: rain, footsteps, dialogue blips, UI clicks and one Eldmere music track from Ninja Adventure's CC0 audio, each with a CREDITS row. Sound effects are converted to mono 22 kHz WAV by `tools/audio/prepare.mjs`; the music ships as the pack's OGG. Audio starts only after the first user interaction. A small settings panel offers mute, music volume and effects volume. CI checks an audio size budget.

**Why.** A few sounds carry most of the mood for very little size; the rest of the soundtrack still arrives in V12.

**Spec.** §4.9, §11; V12 in §12.

---

## P-43 — Buildings block their footprint; actors collide at their feet

**Problem.** Building stamps left their roof row walkable ("behind the roof"), and the cottage door was an open cell inside the wall row. The player could walk into the cottage wall and stand inside it.

**Decision.**
- A building blocks its **whole footprint**, roof included. Every building stamp also writes a `building` object (its footprint) to the map. The server's collision grid is the `collision` layer plus every building footprint, and `MapValidator` fails a map whose `collision` layer leaves a footprint tile open, so the client, which reads only the layer, and the server always agree.
- A **door** is part of the footprint. Until interiors exist it is an `interact` object in front of the wall with flavour text (the cottage: "The door is barred against the storm."); with interiors it becomes an exit.
- The player and NPCs collide with a small **feet-only box** (10 × 6 px) and are depth-sorted by their feet (lower on screen is drawn in front). Roof edges and canopies in the `above` layer still draw over them.

**Why.** Walls that the player can walk into break the illusion at once, and a footprint rule is easy to check by machine.

**Revised by** P-51 (actors stand on whole tiles; the feet box is gone).

**Spec.** §9 (map objects, map rule 8).

---

## P-44 — Paged dialogue and keyboard choices

**Problem.** Long dialogue text scrolled inside a fixed box, the choices waited below it, and choices could only be clicked, tapped or picked with a gamepad.

**Decision.**
- **Pages:** the client splits a node's text into pages by **rendered lines**: 2 lines on desktop and in landscape, 3 in the portrait deck. A `[page]` marker in dialogue text forces a page break. Each page types out; E, Space, Enter, gamepad A or a tap on the box completes it, and the same input again turns the page. A ▼ marker shows when a page is complete and another follows. Choices appear only on the last page.
- **Fast-forward:** holding E (or Space, Enter, gamepad A) skips through pages the player has already read on this profile; unread pages still need a fresh press each.
- **Choices:** W/S or ↑/↓ move a visible highlight, E, Space or Enter take it, and 1–4 take a choice directly. Mouse hover moves the highlight, and clicks and taps still take a choice at once. The gamepad works as before (P-41).

**Why.** Short pages read better on a phone than a scrolling block, and a keyboard player should never need the mouse.

**Revised by** P-49 (page size from the space available, pages by sentences).

**Spec.** §4.8.

---

## P-45 — Story gates are obstacles in the world

**Problem.** A gated exit only answered "The way is closed." and put the player back, which looks like a bug.

**Decision.**
- A path that opens later is closed by an **`obstacle`** map object: a flooded ford, rubble, a fallen tree, crates, a locked gate or a guard. Interacting with it shows flavour text (its `dialogue`). Its tiles are drawn in the map's `obstacles` layer, and it blocks its rectangle **while it is active**, on the client and the server alike. `hideIf` (or `showIf`) names the flag that removes it, so the same object can also change by swapping it for another one.
- A path that never opens in any planned phase is a **natural boundary** (trees, water) with no exit.
- An exit may only lead to an area that has a map; unbuilt areas have no edges yet. Edges keep `gateFlag` as a server-side check.
- **Validator:** every flag that opens or closes a gate (an obstacle's `showIf`/`hideIf`, an edge's `gateFlag`) must be set somewhere in the content, or startup fails. Every obstacle must be reachable.
- Eldmere: the flooded road west (opens with the Whispering Wood) and the north rubble in the Old Ruins (opens in V7) are obstacles without a flag for now; crates block Tanner's Alley until the player follows Corvin (`following_corvin`); the alley's iron gate is locked; Mill Road's south road ends in a natural boundary.

**Why.** The world explains itself: the player sees why a way is closed and can tell it might open.

**Supersedes** the "edges to unbuilt areas must be gated" rule in §9.

**Spec.** §4.11, §9.

---

## P-46 — Interaction marker and current objective

**Problem.** Players could not tell what they can use, and returning players did not know what to do next.

**Decision.**
- A small **marker** shows above the nearest NPC or object in reach: the key or button for the device in use (E, gamepad A, a tap icon).
- The **current objective** is one short line, chosen by the server from `objectives` in `world.json` (the first entry whose conditions hold, using the usual condition format) and sent in `SessionView`. It shows briefly when it changes and always in the pause menu.

**Why.** Both answer "what now?" without a tutorial; the server decides, as with every game rule.

**Spec.** §4.8, §4.16.

---

## P-47 — Layout polish

**Decision.**
- **Portrait deck:** about 28–30 % of the screen height with the same button sizes; Bag and Menu as small pill buttons.
- **Area name:** a title card on entering an area (large, centred, fading after about 2.5 s) instead of a permanent label.
- **Desktop:** larger, higher-contrast HUD numbers; Bag and Menu as small icon buttons with key hints (I opens the Bag, Esc the menu).
- **Landscape:** Bag and Menu are translucent like the D-pad and Ⓐ until pressed.

**Why.** More of the screen goes to the world, and a keyboard player gets a key for every button.

**Spec.** §4.16.

---

## P-48 — An AAC fallback for the music

**Problem.** Safari on older iPhones cannot play OGG Vorbis, so the Eldmere music stayed silent there.

**Decision.** Each music track ships as the pack's OGG plus an AAC (`.m4a`, 64 kbps) fallback. The client plays the OGG where the browser can, otherwise the AAC. `tools/audio/prepare.mjs` decodes the OGG in Playwright's Chromium and encodes the AAC with Windows' Media Foundation (`tools/audio/wav-to-m4a.ps1`), so no new tool is needed. The audio budget counts what one player downloads: the effects plus the larger version of the music.

**Why.** Every phone gets the music without a new dependency, and players never download both versions.

**Spec.** §4.9.

---

## P-49 — Pages by sentences and space, one overlay at a time, message boxes, story cues

**Problem.** Fixed line counts (P-44) wasted space in the portrait deck and split sentences anywhere, sometimes leaving the choices under a scrap of text. On arrival the title card, an opening dialogue and the objective banner could overlap. Signs and obstacles showed a lone "Turn back" choice, and affinity changes showed bare numbers.

**Decision.**
- **Pages:** a page holds as many lines as the box has room for (over the world the box fits its text up to its share of the view; in the deck it fills the deck), and the last page leaves room for the choices. Pages hold whole sentences while they fit; a sentence is split only when it alone exceeds a page. The last two pages are rebalanced so the final page starts a sentence and the two are even. `[page]` still forces a break. Revises the line counts in P-44.
- **Arrival:** the area title card shows first (about 2 s), then any opening dialogue, then the objective banner once the dialogue closes, then hints. Never more than one at once.
- **Message boxes:** a node whose only option is unlocked, ends the dialogue and has no effects is sent as `DialogueView.message`; the client shows a message box (■) closed with E, A or a tap, without a choice button (signs, the cottage door, obstacles, narration).
- **Story cues:** an affinity change shows a cue from `world.json` (`affinityCues`, with the NPC's `shortName`: "Corvin will remember that.") instead of a number; numbers stay in the Bag. Toasts are smaller in portrait and never cover the touch controls.
- **Stepping:** a frame counts as at most 100 ms and physics moves at most 8 px between collision checks, so nothing walks through a one-tile wall on a slow device.

**Why.** Text reads in natural units, the choices always have room, the first seconds in an area stay calm, and the story speaks in its own voice.

**Revised by** P-50 (pages of at most 3 or 4 lines, clause breaks, a deck that fits its dialogue) and P-51 (tile steps replace the physics stepping).

**Spec.** §4.8, §4.16.

---

## P-50 — Short pages, broken at sentences and clauses

**Problem.** Pages sized to all the available space (P-49) made long blocks of text, and the portrait deck kept empty space under short messages.

**Decision.**
- A page holds at most **3 lines** over the world (desktop, landscape) and **4** in the portrait deck, fewer if the box has no room; the last page still leaves room for the choices.
- Pages break between sentences; a sentence too long for a page breaks between clauses (after a comma, semicolon, colon or dash), and only a clause too long for a page breaks between words. The last two pages are still rebalanced: the final page starts a sentence wherever possible (else a clause), and the two are as even as possible. `[page]` still forces a break.
- The box fits its pages, and every page but the last keeps the height of the tallest one, so the box does not jump while paging. In portrait the deck shrinks to fit short messages and pages (up to half the screen), with room for the choices.

**Why.** Three or four lines are read at a glance on a phone, and natural breaks keep each page meaningful.

**Revises** the page size in P-49.

**Spec.** §4.8.

---

## P-51 — Tile-step movement

**Problem.** Free movement with arcade physics (P-06, P-43) kept producing snags and edge cases: feet catching wall corners, corner sliding and corner assist to work around them, sub-pixel overlaps, frame-rate-dependent collision on slow phones, and an e2e walker that had to line the player up with tolerances and retries. Players of top-down monster-battlers also expect grid steps.

**Decision.**
- **One step is one tile.** The player is always on a whole tile; a step slides smoothly to the next tile in `world.stepMs` (160 ms, `balance.json`). Holding a direction keeps walking; a quick tap on a new direction only **turns in place** (the direction must still be held after `world.turnMs` to walk). While walking, a new direction turns and steps at once.
- **Collision is a tile lookup:** the `collision` layer (building footprints included, P-43), active obstacles (P-45), NPC tiles and the map edge. No physics for the player; corner sliding and corner assist are gone.
- **Input is queued per step.** Each key, D-pad or gamepad press is a queued step (or turn), so a press that goes down and up within one long frame still counts. Movement is time-based (the real frame time, capped at 250 ms, carries over between steps), so the walking speed and the tiles reached are the same at any frame rate.
- Exits and triggers fire on arriving at their tile, and stop the walk and its queue. Interactions wait for the current step to finish. NPCs, the interaction marker and the reach checks use the same tiles.
- **Server:** an event's position must be a tile centre; the tile must be walkable, reachable by BFS on the collision grid with this game's obstacles, and within the speed check, which now counts tiles per second from `stepMs` (`1000 / stepMs × elapsed × speedTolerance + speedSlackTiles`).

**Why.** Whole tiles make collision exact and identical on the client and the server, remove a whole class of snag and tunnelling bugs, make every route replayable tile by tile in tests, and match the genre.

**Supersedes** the free-movement parts of P-06 (smooth, not grid-locked movement) and the feet-box collision of P-43; P-49's physics stepping no longer applies. The REST events, BFS and speed check of P-06 and P-19 stay.

**Spec.** §2, §4.11, §4.16, §10, §12.

---

## P-52 — Real-device fixes: zoom on short screens, released controls, readable nights

**Problem.** The first check on a real phone (Chrome, Android) found:
- in landscape with the address bar showing, the view is under 300 px tall, so the 11-tile rule gave a zoom of 1: everything looked tiny, and the 40-tile Square was narrower than the screen, leaving a black band;
- a finger on the D-pad when a dialogue opened never reached the button again (the dialogue takes the D-pad's place in the portrait deck), so the player walked on alone after the dialogue;
- a long press on the controls selected the deck's text, and the browser then cancelled the touch, which stopped the walk and felt like lag;
- the storm night was too dark to see at half brightness;
- an exit stepped on while offline did nothing after reconnecting until the player stepped off and on again.

**Decision.**
- **Zoom:** still a whole number (P-29). The largest that keeps about 11 tiles across the short side; if that is only 1, the largest that keeps 8; and never less than the map needs to fill the view, so no empty band shows beside a small map.
- **Touch controls:** any lift of the finger that holds the D-pad, wherever it lands, losing the page, or the D-pad leaving the screen stops the walk. The world screen allows no text selection and no touch callout.
- **Night light:** every colour channel of an area's ambient light multiplies by at least 0.55 (a content test checks every light state). The storm night is now ambient 0.7 with a softer blue tint and a slightly larger player light.
- **Outages:** a world event that got no answer moves the player, once the game reconnects, to the position in the server's view (V2_SPEC §4.11).

**Why.** These are what a player meets in the first minute on a phone. Each one now has a test, so it stays fixed.

**Spec.** §4.11, §4.16.

---

## P-53 — Full screen on phones; no browser long-press gesture on the controls

**Problem.** On the real phone, Chrome's tab strip and address bar kept a band of a landscape screen, and holding or tapping the D-pad still made Chrome buzz and play its long-press sound every few seconds (P-52 stopped the selection, not the gesture).

**Decision.**
- **Full screen:** on a phone layout, the first tap in the game asks the browser for full screen (the Fullscreen API, the way a video player does; browsers allow it only from a tap). It asks once per visit. If the player leaves full screen, a small full-screen button beside Bag and Menu brings it back. Where the browser has no Fullscreen API (iPhone Safari), nothing changes. Installing the game as an app (PWA, V2c) will give full screen without the tap.
- **Long press:** the D-pad and Ⓐ cancel `touchstart` (a non-passive listener, since React's touch listeners are passive), so the browser starts no long-press gesture. Their pointer events still arrive. The world screen also cancels the context menu.

**Why.** A phone game should own the whole screen, and a held button should only walk.

**Spec.** §4.16.

---

## P-54 — Battle rules for V4

**Problem.** V2_SPEC §4.2 and §4.5–4.7 leave gaps a battle engine must close: how creeps attack and choose moves, the element of Strike, how long Guard lasts, turn order under Frost, status edge cases, the flee "ignore" rule without per-step reporting, and how many enemies a battle has.

**Decision.**
- **1 v 1.** A battle is the player against one creep, or against the single enemy of a dialogue encounter.
- **Moves.** Strike is a Physical weapon move with power 1.0. A creep move's raw damage is `atk × power`. Creeps crit at 5 % (`balance.json`). Each creep has weighted moves (`creeps.json`), chosen with the run's seeded RNG.
- **AI strategies:**
  - `AGGRESSIVE`: weighted picks.
  - `DEFENSIVE`: Guards once below 30 % HP.
  - `AFFLICTER`: prefers a status move whose status the player does not carry.
- **Telegraphs come forward from V7.** A move with `telegraph: true` is announced in the battle text one enemy turn early and executed on the enemy's next turn. Guard cuts it to ×0.25, other hits to ×0.5. Guard lasts until the guarder's next turn.
- **Turn order** is rebuilt every round from current speed, so Frost's −3 counts. The player goes first on ties.
- **Statuses:**
  - Statuses tick at the start of their owner's turn, and a tick can end the battle.
  - An immune hit (×0) applies no status.
  - Status damage ignores defence and is at least 1.
- **Flee** uses current speeds with the v1 chance (50 % ± 5 % per speed point, 10–90 %). A creep the player fled from ignores them:
  - the client re-arms its aggro only after the player has been at least `aggroRadius + 3` tiles away;
  - the server accepts a new encounter with it only after enough time to walk three tiles out and back (`2 × 3 × stepMs ÷ speedTolerance`).
- **Encounter lookup.** The server picks the first living, non-ignored creep within Chebyshev `aggroRadius` of the validated position (ties go to the lowest object name). The request's `creepId` must name that creep. The lookup is a linear scan of the area's creeps; the `SpatialHash` of §4.2 waits until an area holds more than 16 creeps.
- **Element choices for Eldmere's first creeps:**
  - *Mire Slime* is Sylvan: weak to Fire.
  - *Storm Rat* is Ashen, a rat touched by the crater's ash on the storm night: weak to Frost, immune to Fire, resists Crystal.

  The pair teaches that the same skill can be the best or the worst choice.

**Why.** Every rule is decided in content and tests rather than in the engine's guesswork, and seeded fights stay reproducible.

**Revised by** P-64 (the meadow creeps' levels).

**Spec.** §4.2, §4.5, §4.7.

---

## P-55 — Interim progression and recovery until V5

**Problem.** The V2 battle maths needs attributes, and victory and defeat lead to Shards and Lanterns, both of which arrive in V5. V4 needs rules that V5 can replace cleanly.

**Decision.**
- **Stats:** the six attributes exist and are all fixed at 10, so the player is level 1 with 100 HP, 30 FP, DEF 10, speed 6 and the weapon's crit. Weapons get their §4.6 base values and scaling grades, with no upgrades yet. v1 XP and energy are retired: the `XP` effect becomes `SHARDS`, and save format 3 turns old XP into Shards one for one.
- **Skills:** the player starts with **Rending Cut** and **Crystal Pulse** in a 4-slot loadout.
  - **Ember** is earned in Eldmere: Brann teaches it once the player carries the Heartshard ("That's worth the fire").
  - **Frost Shard** stays locked until a later region.
  - A `LEARN_SKILL` effect fills the first empty slot.
- **Victory:**
  - The player gains Shards = `round(35 · level^1.45)`. They are held, shown in the HUD and saved; spending them comes in V5.
  - The creep's drops are rolled.
  - **FP refills to full.**
  - The creep stays down.
- **No HP regeneration.** HP heals only from BAG items. The starting bag holds **3 healing draughts** (40 HP each); the balance runner keeps that number within 2–3.
- **Defeat by a creep:**
  - The player wakes at the region's `respawn` point (Eldmere: the cottage, `home`) with full HP and FP.
  - Creeps respawn with new levels.
  - Held Shards are **kept**.

  V5 changes two lines in `PlayerDefeat`: the place becomes `last_lantern`, and held Shards become Lost Shards.
- **Dialogue fights** (the Hired Blade) keep their dialogue's win, lose and flee nodes. A loss sets HP to 50 %, the v1 rule.
- **Gold** stays the story currency (Corvin's offer). V5 decides whether it merges into Shards.

**Why.** The battle numbers are final from V4, and every interim rule lives in one place that V5 swaps out.

**Replaced by** P-62 (defeat) and P-63 (victory and healing).

**Spec.** §4.1, §4.3, §4.4, §4.6.

---

## P-56 — Idempotency keys in detail

**Problem.** P-31 named the idea; V4 needs the exact contract.

**Decision.**
- **Client:** every state-changing request sends an `Idempotency-Key` header holding a UUID that is new per action:
  - world exit, interact and encounter;
  - dialogue choose;
  - battle action;
  - equip and use;
  - save, load and delete.

  The UUID is built with `crypto.getRandomValues`, since `randomUUID` needs a secure context and phones on the local network use plain http. Without the header the server answers `400 IDEMPOTENCY_KEY_REQUIRED`.
- **Server:** the run keeps the last **8** actions as `{key, requestHash, runVersion}`, written in the same `active_run` update as the action itself.
  - A repeated key with the same request hash applies nothing and returns the **current** view.
  - The same key with a different request returns `422 IDEMPOTENCY_KEY_REUSED`.
  - A save is remembered like any action: it writes its slot and the run, with its key, in one transaction, so a retried save does not write the slot again.
  - Load and delete need the key too but keep no record of it: they are idempotent by nature (loading a slot twice gives the same run; deleting an empty slot succeeds).
  - `/api/game/new` is exempt: it creates a fresh run, and an abandoned one expires.
- **Retry:** after an outage the client re-sends the **same** request with the **same** key once the game reconnects, so an action is never lost and never applied twice. This replaces V3's snap-back after an outage (P-52).

**Why.** Storing the view would bloat every run row; the key, the request's fingerprint and the run version are enough to answer a retry correctly.

**Revises** P-31 (storage) and the outage rule of P-52.

**Spec.** §4.12, §7.

---

## P-57 — Battle presentation

**Problem.** The v1 fight screen was a list of buttons over the world. V4 needs the monster-battler layout on phones and desktop, with the feel of a real battle, within the budgets.

**Decision.**
- **Split:** React draws the info boxes, battle text and menus, so they are accessible and keyboard-, gamepad- and touch-friendly. A Phaser `BattleScene` in the existing world chunk draws the arena (backdrop, sprites, effects); the world scene sleeps meanwhile.
- **Layouts:**
  - *Portrait:* the enemy's info top-left and its sprite top-right; the player's back view bottom-left and info bottom-right; the deck below holds the battle text and **ATTACK · SKILLS · BAG · FLEE** in a 2 × 2 grid.
  - *Landscape:* a full-screen arena with a bottom overlay.
  - *Desktop:* a centred arena with the same overlay.

  Every target is at least 48 px. Keyboard W/S/E/1–4 and Esc (back), gamepad A and B, and touch all work as in dialogue.
- **Battle text:** a round's lines use the dialogue pager. They **auto-advance** after a short pause; one tap or E skips the rest of the round, and holding E fast-forwards. A **Battle text speed** setting offers Slow, Normal, Fast and Instant (default Fast). The menu appears when the text is done. Reduce motion turns off the typewriter, shake and flashes.
- **Feel:**
  - a short transition from the world (the first battle frame within 300 ms of the encounter response);
  - a hit flash, floating damage numbers, draining HP bars;
  - camera shake on crits;
  - idle and attack motion;
  - effect strips from the art manifest;
  - status icons with a shape and a letter.
- **Art and audio** come from the Ninja Adventure pack (monsters, effects, a human fighter for the Hired Blade, hit and magic sounds, victory and defeat stings, a battle track). The backdrop is an original composite of pack tiles made by `tools/art/backdrop.mjs`. Battle art is loaded only by the Phaser chunk, never by the first page.
- **Audio budget:** effects plus the largest region track plus the battle track, raised to **2,048 KB**. The battle track loads **lazily**, starting when a creep comes within aggro range, and is never part of the first page load.

**Why.** It is the layout the vision promises, it reads well on a phone, and it stays inside the performance budgets.

**Revised by** P-59 (the audio budget is split by when sounds download).

**Spec.** §1, §4.7, §4.9, §4.13, §4.16.

---

## P-58 — Dependency upkeep before V5

**Problem.** Dependabot had eight PRs open after V4: two grouped updates, four major frontend updates, a major PostgreSQL image and framer-motion 14. Some failed CI on their own.

**Decision.**
- **Grouped updates** go in as they are: the Maven wrapper 3.3.4 with Maven 3.10.1, and the GitHub Actions group (checkout v7, setup-node v7, setup-java v6, upload-artifact v7, release-please-action v5, gitleaks-action v3).
- **Majors one at a time**, each with green CI:
  - **Vite 8** with **@vitejs/plugin-react 6** together, since plugin-react 6 requires Vite 8. No code changes.
  - **TypeScript 7** type-checks the frontend with no code changes, about 7 times faster. It ships no JavaScript compiler API, which openapi-typescript (latest 7.13) needs from a TypeScript 5 peer to generate the API client. So `typescript` stays at **5.9.3** for the generator, and TypeScript 7 is installed as **`typescript7`** (an npm alias), which the `typecheck` and `build` scripts run by path. The alias goes once openapi-typescript supports TypeScript 7.
  - **Vitest 5**: no code or configuration changes.
- **PostgreSQL stays on 17** (P-21) until the hosting is chosen. Dependabot ignores major `postgres` image updates; minor and patch images still arrive. The PostgreSQL 18 PR is closed.
- **framer-motion 14** is not part of this round; it waits for its own review.

**Why.** Small, reviewed steps keep the trunk green, and the database's major version belongs to the deploy decision.

**Spec.** §2.

---

## P-59 — Audio budgets by download

**Problem.** One audio budget (2,048 KB for every effect, the region's music and the battle track) said nothing about what a player waits for: effects were fetched on first play, music when a region started, and the total was never downloaded at once.

**Decision.** `content/audio/audio.json` declares **packs**, and each sound belongs to exactly one:
- the **initial pack** (interface sounds: blip, click) loads with the first page;
- each **region pack** (the region's music and ambience: Eldmere's track, rain, footsteps) loads on entering the region;
- the **battle pack** (the battle track and the nine battle effects) loads only when a creep is near (P-57).

The audio engine fetches a pack's files when its moment comes and decodes each effect on first play. CI checks each pack against its own budget in `balance.json`, counting the larger of a track's OGG and AAC files, and fails a build with audio that no pack loads:

| Pack | Budget | Now | Why this limit |
|---|---|---|---|
| Initial | **64 KB** | 22 KB | The first page must stay light; room for a few more interface sounds. |
| Each region | **1,280 KB** | Eldmere 1,067 KB | V3's original audio budget (effects and one region track), downloaded behind the area title; room for one more ambience loop. |
| Battle | **1,024 KB** | 944 KB | Must arrive between "a creep is near" and the battle; boss themes will need their own pack. |

**Why.** A budget per moment of download matches what a player on a phone actually waits for, and a new region or sound cannot hide inside one big number.

**Revises** P-57 (audio budget).

**Revised by** P-64 (region packs may share effects; boss music gets its own pack in V7).

**Spec.** §4.13.

---

## P-60 — Shardbound's own Phaser build

**Problem.** The lazy Phaser chunk was 383 KB gzip (budget 450), almost all of it Phaser's prebuilt bundle, which includes Matter and Arcade physics, Phaser's sound system, video, DOM elements, meshes and other features the game never uses. The prebuilt ES module cannot be tree-shaken.

**Options measured** (the chunk in KB gzip, prebuilt bundle = 381):

| Option | Chunk | Saves |
|---|---|---|
| Phaser from source, everything | 377 | 4 |
| Phaser's no-physics entry | 333 | 48 |
| … without Phaser's sound system | 326 | 55 |
| … and without the Canvas renderer | 319 | 62 |
| **Own entry: Phaser core plus the modules we use, no Phaser sound** | **275** | **106** |
| A separate on-demand battle chunk | about 380 | about 3 (the battle scene's code), plus a fetch at battle start |

**Decision.** `frontend/src/world/phaserBuild.cjs` builds Phaser from its source: Phaser's own core entry plus tilemaps, particles, tile sprites, containers, ellipse and rectangle shapes, `Display.Color` and `Math.Clamp`. `vite.config.ts` points `phaser` at it and sets Phaser's build flags: both renderers (Canvas stays as the fallback), no WebGL debug, no Phaser sound (the game's sound is its own Web Audio engine), no 3D camera or Facebook plugins. Types still come from the `phaser` package. The battle code stays in the world chunk.

**Why.** It saves 28 % of the chunk with one file and a few lines of configuration, and no new dependency. A separate battle chunk would save almost nothing, because both scenes need the whole engine, and it would add a download to the 300 ms battle start. Dropping Canvas would save 7 KB more but remove the fallback for phones without WebGL.

**Consequence.** A Phaser class or factory used for the first time must be added to `phaserBuild.cjs`. The e2e suite (world, battle and every layout) is the check, since a missing module only fails at runtime.

**Spec.** §4.13.

---

## P-61 — Lanterns and levelling

**Problem.** V5 brings Souls-like progression to Eldmere: places to rest, and levels bought with Shards. The spec (§4.1, §4.3) leaves open how a Lantern is placed and lit, how a rest stays consistent with the autosave, and how the level-up screen stays responsive on a phone.

**Decision.**
- **Lanterns are map objects:** `lantern` with `lanternId`, `name` and `spawn` (the spawn point where the player wakes). Eldmere's first is the **Junction Lantern** on Cottage Lane, at the reserved spot. `MapValidator` checks unique ids, an existing spawn and reachability.
- **Kindling:** a Lantern is cold until the player carries the Heartshard; interacting then lights it once. A lit Lantern **stays lit and usable without the Heartshard** (after selling it to Corvin, for example). Without the shard, a cold Lantern only shows a message.
- **Rest** (interacting with a lit Lantern) is **one transaction**, keyed like every action (P-56):
  1. HP and FP to max, and Lantern Draught charges refilled (P-63);
  2. defeated creeps respawn with rerolled levels;
  3. the Lantern becomes `lastLantern`;
  4. slot 0 is written synchronously in the same transaction (reseed → snapshot → write);
  5. the **Lantern menu** opens.
- **The menu:** **LEVEL UP · SKILLS · LEAVE**. Fast travel joins it with the map in V8.
- **Levels:** `balance.json` holds the cost curve `cost(L) = round(100 · L^1.5)`, the level cap (99) and the attribute cap (99). Several levels can be bought in one confirm. The server computes the **preview** (`POST /api/lantern/levelup/preview`, read-only, unkeyed): the level, the cost and the Shards left, and HP, FP, DEF, AR and speed before → after. The confirm is keyed.
- **Input never waits on the network:** + and − change the pending values at once; only the preview numbers update when the server answers, and stale answers are ignored. Confirm sends the pending values and the server checks them again.
- **Hints:** the first Lantern in reach shows "rest here", and the first menu explains levelling and resting, once per profile.
- **Osric the Lamplighter** stands beside the Junction Lantern. He explains the Lanterns, the Shards and the Long Dusk, hints at the roads ahead, and has a one-time line after the player's first death.

**Why.** Rest and autosave in one transaction can never disagree. A Lantern lit for good keeps the merchant route playable. A preview from the server keeps the rules on the server (rule 1), and pending values that don't wait on the network keep the screen responsive.

**Spec.** §4.1, §4.3, §4.8.

---

## P-62 — Death and Lost Shards

**Problem.** P-55's interim defeat sent the player home and kept their Shards. V5 needs the risk of §4.4.

**Decision.** One seam, `engine/Death`, replaces P-55's defeat:
1. Any Lost Shards still on the map are destroyed.
2. Held Shards become **Lost Shards** at the tile where the battle started; held Shards go to 0. No marker if there were none.
3. The player wakes at `lastLantern`, or at the region's `respawn` point if no Lantern is lit yet, with full HP and FP, a refilled Lantern Draught, and creeps respawned.
4. **Recovery:** stepping within one tile of the marker sends `POST /api/world/recover` (keyed, the usual world-event checks, Chebyshev distance ≤ 1). The Shards return and the toast "Shards recovered" shows.
5. A deaths counter (a flag) drives Osric's one-time line.
6. What happened ("The Shards you lost before fade into the dark.", "You wake beside the Junction Lantern.") closes the battle's text, read before Continue; a toast would be hidden behind the battle.

**Dialogue fights** (the Hired Blade) keep their story outcome on a loss (the lose node, HP 50 %) and drop no Lost Shards.

**Why.** §4.4's risk, with one place to change it, and story fights stay story.

**Replaces** P-55 (defeat).

**Spec.** §4.4.

---

## P-63 — The Lantern Draught, and FP after victory

**Problem.** P-55's three consumable draughts and the full FP refill after every victory were stand-ins for Lanterns.

**Decision.**
- **Lantern Draught:** a refillable flask (`lantern_draught`, item kind `FLASK`) with **3 charges**, each healing **40 % of max HP**. It is refilled at every rest and on death. It shows as "Lantern Draught 2/3" in the battle BAG and in the Bag, and uses the existing item action. More charges and potency come with V6.
- `healing_draught` is retired. Save format 4 turns any stack into a full Lantern Draught, and the Mire Slime drops the Azure Tonic (+10 FP) instead.
- **FP after victory:** **25 % of max FP** (`battle.victoryFpPct`), not a full refill. Lanterns are the full refill now.

**Balance impact.** The flask heals up to 3 × 40 % *between rests* instead of 120 HP for the whole region, so running low means going back to the Lantern rather than losing the run. 25 % FP keeps the skills (and the creeps' weaknesses) in use without making them free. The balance runner checks both.

**Measured (V5).** The runner's FP variants barely differ in Eldmere: mean turns against the Hedge Adder are 3.36 with no FP after victory, 2.96 with 25 % and 2.92 with a full refill. The route rests every two fights, so the Lantern does most of the refilling; 25 % keeps a skill ready for a second fight without a rest. Revisit in the Whispering Wood, where the stretches between Lanterns are longer.

**Replaces** P-55 (victory and healing).

**Spec.** §1 (Lantern Draught), §4.1.

---

## P-64 — Eldmere's creeps, gold, Lantern audio

**Problem.** Four reserved creep spots were empty, Eldmere needs its 8 fights to reach level 4 before the boss (§10), and the first level-up must come after fight 3 (§12).

**Decision.**
- **Creeps and levels:**

  | Spot | Creep | Family | Level | Shown |
  |---|---|---|---|---|
  | Cottage Lane meadow B | Mire Slime | Sylvan | **1** (was 1–2) | always |
  | Cottage Lane meadow A | Storm Rat | Ashen (unchanged since V4) | **1** (was 2–3) | always |
  | Mill Road field A | Furrow Mole | Beast | 2–3 | always (Mill Road opens at dawn) |
  | Mill Road field B | Hedge Adder | Beast | 2–3 | always |
  | Old Ruins A | Ash Wisp | Ashen | 3–4 | from dawn |
  | Old Ruins B | Barrow Skull | Undead | 4 | from dawn |

  Both meadow creeps at level 1 give 70 Shards after two fights and 166 after a third at level 2, so the **first level-up comes after fight 3**. The ruins creeps appear at dawn, so the storm-night walk for the Heartshard stays a level-1 walk. One pass of Eldmere's fights earns about 980 Shards, enough for **level 4** (100 + 283 + 520).
- **Weakness rule:** every creep's weakness must be exploitable by a skill the player can have when first meeting it, or be documented here as a deliberate **teaser** for a later skill:

  | Creep | Family | Weakness | First met | Skill available then | Status |
  |---|---|---|---|---|---|
  | Mire Slime | Sylvan | Fire | storm night (lane) | Rending Cut, Crystal Pulse; Ember from Brann at dawn | teaser on the storm night; exploitable on refights after dawn |
  | Storm Rat | Ashen | Frost | storm night (lane) | Rending Cut, Crystal Pulse (Crystal is resisted) | **teaser**: Frost Shard comes in a later region; Rending Cut's Bleed is the answer |
  | Furrow Mole | Beast | Fire | dawn (Mill Road) | Ember | exploitable |
  | Hedge Adder | Beast | Fire | dawn (Mill Road) | Ember | exploitable |
  | Ash Wisp | Ashen | Frost | dawn (ruins) | Ember (immune), Crystal Pulse (resisted) | **teaser**, deliberate before the Ashen Herald: fire does not burn the Ashen |
  | Barrow Skull | Undead | Holy | dawn (ruins) | Crystal Pulse and Ember neutral; Strike resisted | **teaser**: Holy skills come later; it teaches skills against Physical resistance |
  | Hired Blade | Human | none | the alley | — | no weakness |

- **Gold** stays story money (the merchant ending's 300 gold). Shards are XP and currency (§4.3). V6 decides whether shops take gold.
- **Lantern audio:** the kindle, rest, level-up and recovery sounds are needed in every region, so they go in **each region's pack**. P-59's rule becomes: initial and battle effects are in exactly one pack; region packs may share effects, and CI counts a shared effect in every region pack that lists it (the browser downloads it once). Eldmere's pack stays within 1,280 KB.
- **V7:** boss music gets **its own pack, loaded at the boss arena**, with its own budget.

- **Tuning and result (V5):** the balance runner (V2_SPEC §10.2) set Furrow Mole HP 92, Hedge Adder HP 92, Ash Wisp ATK 11, and Barrow Skull ATK 11 and HP 60, so every creep fight takes a median of 3–6 turns. Over 1,000 seeds: first level-up after fight 3, median level 4 before the boss, about 0.06 deaths per run, and the boss stand-in (Hired Blade at level 5, HP ×1.8) won 84 % on the first attempt.

**Why.** It fits §10's curve exactly, keeps the story's order safe, and makes every weakness either usable or a lesson.

**Revises** P-54 (meadow levels) and P-59 (shared region effects).

**Spec.** §4.2, §4.3, §10.

## P-65 — V6 split; the gear slice (chests, tier colours, compare and equip)

**Problem.** V7 tunes the Ashen Herald "for an unupgraded weapon", but every Eldmere player still fights with the Rusty Shortsword: no chest exists and the reserved chest spots are empty. Smith upgrades, materials and quick slots are large and can wait until after ALPHA. The Bag can equip a weapon but cannot show what changes, weapon cards never showed their attack, and tiers F–C were four near-identical greys.

**Decision.**
- **V6 splits.** **V6a Gear slice** comes now, before V7. **V6b Gear II** (smith upgrades +0..+10, materials, quick slots, the remaining chests) comes after ALPHA, first in the post-ALPHA order.
- **Chests** use the map object `chest{item, qty, flag}` (V2_SPEC §9):
  - **Opening:** through the keyed `POST /api/world/interact`, in WORLD mode and in range. It grants the item, sets the flag, and shows a `LOOT` toast carrying the item's tier.
  - **Opened state** is the chest's flag. It's per run and saved with the other flags, like a lit Lantern (P-61), so the save format does not change. A v4 golden fixture and resume tests pin it.
  - **A full bag** keeps the chest closed ("Your bag is full"), so nothing is lost.
  - **Never empty:** every chest whose item doesn't stack names a fallback in content: `fallbackLevel` (Shards worth one creep of that level, from `balance.json` `creepShards`), or `fallbackItem`/`fallbackQty`. `MapValidator` requires it. If the player already owns the item, the chest grants the fallback, and a toast says so. This applies to every future chest.
  - Contents are not sent to the client before the chest is opened.
- **The two Eldmere chests** (both shown from dawn, so the storm night stays the tuned level-1 walk):

  | Chest | Where | Weapon | Fallback | Why |
  |---|---|---|---|---|
  | `ruins_chest` (main path) | Old Ruins (24, 15), in view on the way out with the Heartshard and on every later trip | **Woodcutter's Hatchet** (E · 28 · Str C · crit 5) | Shards of one level-3 creep | One tier up (AR 20 → 31 at the start); Strength scaling suits the common Vig/Str build |
  | `clearing_chest` (secret, never required) | Cottage Lane's hidden clearing (28, 18), through the gap behind the south-east trees | **Hunter's Longbow** (D · **26**, was 30 · Dex B · crit 10) | Azure Tonic | Slightly better and specialised: AR 31 at the start like the hatchet, but steep Dexterity scaling and double crit |

  - **Finding the clearing** (all optional): a faint glint at the closed chest, visible through the treeline; a broken-branch decal at the gap; and one Osric hub choice from dawn ("Anything worth finding nearby?") mentioning an old poacher's path behind the south trees.
  - **The alley spoils:** on the merchant route, if the player already owns the hatchet, "Take the hatchet" becomes "Take the tonic from his belt" (an Azure Tonic). It's not gold, which would change the merchant ending's 300-gold maths, and not Shards, which come from ash-touched things.
  - The other reserved chest spots (Mill Road's stream nook, the Square's nook by Maren) wait for V6b.
- **Weapon cards** show the tier badge, the name, the attack rating **as computed by the server for this player**, crit, Str/Dex/Arc grades and the description.
  - **Tier colours:** F–C climb a metal ladder, F ash grey, E stone, D bronze, C silver with a still sheen, so early finds feel like upgrades; B green, A blue, S purple, SS gold shimmer, SSS animated rainbow (magenta where one colour is needed). No two tiers share a hue family (`tiers.test.ts`).
  - Badge text keeps at least 4.5:1 contrast. Under reduced motion SS and SSS keep their gradients without the animation.
- **Compare and equip** in the Bag, outside battle:
  - The equipped weapon and the selected one side by side, with AR, crit and the three grades before → after, then **Equip** (the existing keyed `/api/inventory/equip`).
  - All values come from the server's view, so the page never waits on the network.
  - Keyboard, gamepad and touch in all three layouts, with targets ≥ 48 px. Walking pauses while the Bag is open.
- **Balance:**
  - `EldmereProgressionTest` takes and equips the hatchet at dawn. Creep fights must stay at a median of 3–6 turns.
  - P-64's boss stand-in band (70–85 % at HP ×1.8) stays asserted for the **Rusty Shortsword** (the lower bound: a player who skipped the chest).
  - The hatchet's rate and the multiplier that restores the band are reported here for V7, together with the secret longbow's effect.

**Why.** V7 can then tune the first boss for the weapon every player actually carries. The chest rules (server-validated, keyed, never empty, never lost to a full bag) hold for every later chest. Showing the server's own numbers keeps the rules on the server (rule 1).

- **After the first screenshots (approved review):**
  - **Loot toasts:** the tier badge sits inline before the find's name, which takes the tier colour. When the server rates the find above the equipped weapon (`ToastView.upgrade`), a device-aware line says where to compare it ("Better than your blade. Press I to compare." / "Tap Bag to compare." / "Open the Bag to compare."). Sideways the toast is small and sits at the top centre, clear of the chest and the player.
  - **The Bag:** its tabs share one row, the close button keeps the top-right corner, and the character sheet stacks so nothing overflows. Sideways, Compare has the panel to itself (Back returns to the sheet and tabs). Toasts other than errors stay hidden while the Bag is open.
  - **Colours:** Shards are always the Heartshard's green and gold is always gold, wherever they show.
  - **Chests:** an opened chest shows its lid thrown back and its empty inside, a little dimmer than a closed one (`tools/art/chest.mjs`); a closed chest glints.
  - **The tier gallery** (`frontend/dev/tiers.html`, dev only) shows the real weapons from `world.json`, rated by the server through `GET /api/debug/weapons` (dev and test profiles only).
- **Measured (V6a, `EldmereProgressionTest`, 1,000 seeds):**

  | Route | Stand-in won at HP ×1.8 | Won at HP ×2.8 | The 70–85 % band returns at |
  |---|---|---|---|
  | Rusty Shortsword (skipped the chest; asserted) | 84 % | — | ×1.8 |
  | **Woodcutter's Hatchet** (main path) | 99 % | 81 % | **×2.8**: V7's starting point for the Ashen Herald |
  | Hatchet and the secret Longbow | 99 % | 87 % | ×2.9 |

  With the hatchet, Eldmere still ends at level 4 and creep fights take a median of 3–5 turns (most at 3, the bottom of the band). The longbow was first measured at base 30: the bot held it in every run and won the ×2.8 stand-in 98 %, far more than "slightly better", so its base became **26**. At 26 the bot holds it in 645 of 1,000 runs and wins 87 % at ×2.8.

**Revises** P-64's stand-in (now the rusty-sword lower bound), P-27's post-ALPHA order (V6 → V6b) and the Hunter's Longbow's base in §4.6 (30 → 26).

**Spec.** §4.6, §9, §12.

## P-66 — No public alpha: one launch, complete, as 1.0

**Problem.** The plan shipped a public alpha after Eldmere (ALPHA) and Whispering Wood as Alpha 2, so accounts, deploy and a public feedback loop came before most of the game existed. The game should meet the public once, complete.

**Decision.**
- **There is no public alpha.** The game launches once, complete, as **1.0**. Until then it runs on a **private, password-protected staging** for invited playtests.
- **Phase order:**

  V7 Eldmere boss → V6b Gear II → V10a Content tools → S1 Private staging → **PLAYTEST 1** (Eldmere) → WW Whispering Wood → V8 Navigation → V11a Hollow Deep → V9 Save slots → **PLAYTEST 2** → V11b Valdren City → V11c Ashen Wastes → V11d Crystal Spire → V2a Accounts + privacy → V2b Email accounts → V10b Telemetry → V12 Polish → **PLAYTEST 3** → **LAUNCH 1.0**.
- **V10 splits:** **V10a Content tools** (the playtest bot over REST, the per-region balance simulator, content tooling) comes early so every region after Eldmere is built with it; **V10b Telemetry** (anonymous telemetry, opt-out, SQL views) comes before launch.
- **V2c's parts are placed:**
  - **S1 Private staging:** the container image, hosting, managed Postgres with backups and a **tested restore**, Sentry, JSON logs, Actuator health and metrics, password protection, preview deployments per PR, and the content-id guard (P-28), since playtesters' saves must survive releases.
  - **V12 Polish:** the installable PWA and version skew (`X-API-Version`, "Update available", one release of backward compatibility, the `oasdiff` check).
- **Playtests:**
  - **PLAYTEST 1** plays Eldmere, start to the Ashen Herald, on phone and desktop with the in-game feedback button (moved from ALPHA).
  - **PLAYTEST 2** plays through the Hollow Deep with save slots.
  - **PLAYTEST 3** plays the complete game after V12.
- **The ALPHA checklist becomes the LAUNCH checklist:** ASVS L1 review, a backup restore drill, a Sentry alert, the privacy page live, licences chosen (code vs story/art), no endpoint accepting a player id from the client (P-32), the repository public, and domain email with SPF, DKIM and DMARC.
- **Releases:** each phase is still a `0.x` release with a CHANGELOG entry; **1.0 is the launch**.
- **GitHub:** the milestone ALPHA is renamed **LAUNCH**; its issues stay on it.
- **Credits and the end screen** belong to V11d, the final ending. V7 ends in an epilogue and free roam, not an end-of-alpha screen.

**Why.** Players see one finished game. Private playtests at three points still bring outside eyes early, and the tools (bot, simulator) arrive before the regions they check.

**Supersedes** the ALPHA parts of P-17 (order, feedback button), P-20 (V2c), P-27 (Alpha 2 and the order after ALPHA) and P-32 (an ALPHA checklist item, now LAUNCH), and the ALPHA milestone. **Reads as LAUNCH** wherever P-11 ("at alpha") and P-25 ("before ALPHA") name the alpha.

**Spec.** §1, §4.10, §4.15, §6, §7, §11, §12, §13.

## P-67 — Eldmere's finale: the Ashen Herald

**Problem.** Every Eldmere route except the merchant's fell into V3's stub `p11` "The Cult Raid", whose resolver always picked `END_WANDERER`. Under P-66 V7 ends Eldmere in an epilogue and free roam, so the region needs its own finale.

**Decision.**
- **The trigger, "The Ash Falls":** `p11` is rewritten in place, so all seven routes into it (Maren, Corvin's "Go home", the alley loss, the millstone, the forest sign) lead to the Herald. Maren sends the player to the rubble north of the crater; the choice sets `herald_risen`. The merchant route (`END_QUICK`, "The Merchant's Bargain") is untouched. `finale.json` now starts at `c5_resolver`: the five resolver endings are parked for V11d.
- **The way in:** the Old Ruins' north rubble becomes an obstacle hidden by `herald_risen`, and `arena_way` becomes the exit to a new area, **The Ash Rise** (edge `gateFlag: herald_risen`).
- **The Ruins Lantern,** a few steps from the Rise, kindles from the crater's lingering warmth from dawn, **without the Heartshard**, so the retry loop is short for every player, including one who sold the stone.
- **Retry loop:** a death goes through `Death` like any creep battle (Lost Shards on the battle tile, wake at the last Lantern). The intro never replays; a retry shows only the boss name banner, then the fight.
- **The boss** (`content/bosses.json`): the **Ashen Herald**, level 5, family Ashen (Fire and Burn immune, Crystal resisted, Bleed works), drawn with the pack's **Red Tengu** (its transformation plays at phase 2). Final stats: **base HP 132, ATK 15, DEF 8, SPD 7**.
  - **Cadence:** every n-th of its moves is the phase's telegraph, which takes two of its turns (announced, then landed), so it is announced every (n + 1)-th round. (A first version announced it every n-th round; with n = 2 the second phase never struck an ordinary blow, so Guarding every other round made it harmless. Found by the balance run and fixed with a failing test.)
  - Phase 1: weighted Cinder Lash, Ash Veil (Bleed) and, every 3rd move, the telegraphed **Cinderfall** (×1.6).
  - Phase 2 at HP ≤ 50 %: a transition line, Stun immunity for 1 turn, speed +1; Searing Wing (Burn), Cinder Lash and, every 2nd move, the telegraphed **Pyre Descent** (×1.9).
  - Flee is disabled, status chances are halved, and a Stun gives 3 turns of Stun immunity (§4.5). The phase is derived from HP, so nothing new is saved for it.
- **The mechanic, "Guard the fall":** Guarding a telegraphed blow cuts it to ×0.25 (as for creeps) and, against the Herald, **cracks its ash mantle**: the Herald is **Exposed** (DEF −50 %) for 2 turns. Only a guarded telegraph exposes it; a guarded ordinary blow does not. It works because Guard goes first (P-69): seeing the warning and choosing Guard always braces the blow. Data: `bosses.json` `onGuardedTelegraph`, status `EXPOSED`.
- **Teaching it:** while a telegraph is pending (the server's `brace` cue, strings from content: `bosses.json` for the Herald, `balance.json` `battle.brace` for creeps):
  - the warning reads "⚠ Cinderfall next turn — Guard to brace", on one line in every layout, on the boss bar or the enemy's info box until the blow lands;
  - ATTACK and Guard carry the warning's amber, and Guard's subtitle becomes "Brace — leaves it exposed" (creeps: "Brace against the blow"; otherwise "Halve the next hit");
  - guarding the Herald's telegraph reads "The Herald staggers — EXPOSED!", and the boss bar shows the boss's status chips (EXPOSED, Burn, Bleed…), since a boss has no enemy info box.
- **Wind-ups are readable in both phases** (creeps too): while a telegraph is pending the enemy glows brighter and back, slowly (never a strobe), and rises a little and settles, on top of the boss's raised-wing pose. Reduce motion: a steady brighter glow, no pulse, no rise.
- **Phase two is an escalation:** the transformation with one short ember flash and a 250 ms shake (reduce motion: one soft, dim flash, no shake); from then on an ember glow on the Herald and a hotter boss-bar fill; the boss track steps up (faster with its pitch kept, and louder: `audio.json` `escalated`, no new pack).
- **The intro's first line** depends on the Heartshard: holding it, "Little lamp-bearer. You carry what was ours."; sold, "Little lamp-bearer. You carried what was ours. The stink of it is on you still."
- **Presentation:** a boss bar just above the battle text, aligned to its right edge so it never covers the player's sprite (at the top of the deck in portrait; in battle there is no D-pad or Ⓐ on touch), with the name, a long HP bar, the phase-2 tick, the phase, the status chips and the telegraph warning; a boss has no enemy info box, so the player's box moves to its top-left place; a name banner at the start of every attempt and **FOE VANQUISHED** on victory, both in the open ground between the fighters. Boss music in its own pack, loaded on entering the Rise, with its own budget (P-64).
- **The ash falls before it can stop:** a light state `ash_falling` (set by `herald_risen`) brings slow grey ash over Eldmere, as "The Ash Falls" says (it settles on the square too): a few dozen flecks a screen from one particle emitter, from `grades.json`'s ash settings. The epilogue's LIGHT step to `ash_lifted` fades it out over 1 s, just before "The ash stops falling", and it stays off. Reduce motion: half the flecks at half the speed. Within the 4× CPU budget (the perf test walks the ruins and the Rise in it).
- **Reward:** `5 · creepShards(5)` = 1,805 Shards and the **Iron Longsword** (C; the Herald's raiders took it from Brann's rack). Never empty (P-65): if it is already owned, the fallback is Shards of one level-5 creep (361). **Whispering Wood's balance assumes the player holds the Iron Longsword after the Herald.**
- **After victory:** FOE VANQUISHED, the rewards, then the epilogue cutscene: the ash stops falling (Eldmere's light state `ash_lifted`) and the flood on the west road starts to drop. Free roam returns with the post-boss state saved (`herald_defeated`, set only by the engine): the Rise is quiet; Osric, Maren and Brann have new lines; Lanterns, creeps, chests and refights keep working. Cottage Lane's flooded road gives way to mud and ends at a stop at the map's edge, "The far bank is still sodden; tomorrow, perhaps.", which the WW phase removes. No end screen and no credits (V11d).
- **Balance** (`EldmereProgressionTest`, 1,000 seeds, the real Herald, under P-69; a loss rests at the Ruins Lantern, picks the Shards up and retries, up to six fights). Two policies:
  - the **guarding** bot Guards a pending telegraph (unless it must drink) and drinks below 40 %;
  - the **naive** bot never Guards, uses skills and strikes, and drinks below 35 %.

  | Route (level 4) | First-attempt wins | Attempts per run | First fight: median turns, draughts |
  |---|---|---|---|
  | Hatchet, Vig/Str, guarding | **79 %** (asserted 70–85 %) | 1.26 | 11, 2 (asserted 8–12 turns) |
  | Hatchet, Vig/Str, never guarding | **43 %** (asserted 35–50 %) | 2.21 | 9, 2 |
  | Longbow, Dex, guarding | **84 %** (asserted ≤ 90 %) | 1.19 | 11, 2 |
  | Rusty Shortsword only, guarding | 33 % (reported) | 2.62 | 17, 3 |

  The rusty-sword route never wins in six fights in 7 % of runs: the fight sends a player who skipped the main-path chest back for it. Getting both policies into their bands needed the telegraphs weaker and the ordinary blows harder than the first draft: base HP 196 → 132, ATK 13 → 15, DEF 10 → 8, Cinderfall ×2.0 → ×1.6, Pyre Descent ×2.4 → ×1.9. Creep deaths per run are 0.00 (hatchet) and 0.06 (rusty), under §10.3's ~0.1, because the bot never misplays: PLAYTEST 1 checks it. Creep fights keep a median of 3 turns (the Barrow Skull 5), unchanged by P-69.
- **Save:** no format bump. The slot document's shape does not change (boss defeat, seen cutscenes and the Ruins Lantern are flags; Exposed is a status); the active run gains an optional `cutscene` id that older runs read as null.
- **e2e:** an `e2e` Spring profile (off in production and dev) adds `POST /api/debug/ready-for-the-herald` (keyed): an engine *rehearsal* that leaves the run where the elder's path does (its flags, quest, standing and the Heartshard, matched against the played route by `RehearsalTest`), at the Ruins Lantern, level 4 with the hatchet and Ember. Only the win test (and the on-demand screenshots) use it; the skip, loss and retry test plays the elder's path through the UI. `ProductionProfileIT` proves the `prod` profile answers 404 for it and for the dev routes, and the bundle grep keeps its path out of the frontend.

**Why.** Eldmere gets a real ending that teaches its last lesson, without an end screen, and the region's routes all converge on it while the merchant's secret stays secret.

**Supersedes** D3 (the raid folds into the resolver) and V3's interim finale; amends P-61 (a Lantern may kindle without the Heartshard).

**Spec.** §4.7, §8, §9, §10, §12.

## P-68 — Data-driven cutscenes

**Problem.** The boss intro and the epilogue need staging (movement, camera, music, lines) that dialogue alone cannot express, and the art stays CC0-pack only.

**Decision.**
- `content/cutscenes/<id>.json` (schema-validated): actors (sprite, tile, facing, and a `name` that titles their lines) and steps `say` (with optional `conditions` variants), `move`, `face`, `camera` (frame, pan, zoom, shake, follow), `music`, `sound`, `fade`, `flash`, `wait`, `light`, `vanish`; `then` is `battle:<bossId>` or `world`. Validated at startup (sprites, tiles, speakers, targets).
- The server owns when a cutscene plays and what follows: a new `CUTSCENE` mode; `POST /api/cutscene/finish` (keyed) ends it, played out or skipped, records it as seen (engine flag `_seen_<id>`) and applies `then`. A seen cutscene never plays again in that run.
- The client plays the steps with map sprites, the dialogue box and pack effects; no illustrated stills.
- **Input:** tapping E or Ⓐ (or the line itself) advances `say` steps; skipping needs a ~1 s hold of E, Ⓐ or the Skip button (≥ 48 px, top-right), whose progress ring fills as it is held; the first press shows "Hold E to skip" ("Hold Ⓐ…", or "Hold to skip" on touch), and letting go early skips nothing. The HUD and the touch controls hide while a cutscene plays. **Reduce motion** removes shake, flashes and camera moves (pans become cuts); fades stay as short cross-fades.
- **Framing:** `camera: frame` centres one actor, or every actor and the player, in the play area above the line box (the dialogue box over the world, nothing in the portrait deck) with a margin, at the largest whole zoom that fits while the map still fills the view (P-29, P-52), clamped to the map. One helper serves every layout, and the scene re-frames on resize and rotation. Actors stand fully on the map. The e2e test checks, in all three layouts, that the Herald and the player are fully inside the play area during the intro.
- **Nothing else on screen:** in CUTSCENE mode the HUD, the touch controls, the area title card, the objective banner, hints and the interaction marker are hidden; an objective that changed meanwhile (or under a battle) shows once the cutscene has ended.
- **Division of work:** React plays `say`, `music` and `sound`; the world scene stages the rest and reports each step done (with a time-out, so a scene still loading never stalls the cutscene). After a boss victory the epilogue waits until the battle's last page has been read.

**Why.** Staging is data like the rest of the content, the server keeps the rules, and a death never replays an intro.

**Spec.** §4.7, §8, §9.

## P-69 — Guard goes first

**Problem.** Turn order was by speed for every action, and Guard lasted until the guarder's next turn. Against a faster enemy (every Eldmere creep from level 2, and the Ashen Herald at speed 7–8 against a level-4 player's 6) a telegraphed blow landed at the start of the round in which the player first saw its warning, so a Guard chosen on the warning always came too late. "Guard to brace" (P-67) would have taught something that could not work; the Storm Rat test pinned that behaviour.

**Decision.**
- **Guard has priority, for every fighter** (player, creeps, bosses): whoever guards raises it before anyone acts, and it lasts to the end of that round, so the hit it blunts is the one that comes in the same round. Flasks, skills, attacks and Flee keep speed order (the player on ties).
- A creep decides its action as the round begins, so its own Guard (the DEFENSIVE strategy, once below 30 % HP) goes first too; a boss follows its cadence at its turn.
- A guarded blow takes ×0.5, a guarded telegraph ×0.25; only a guarded telegraph exposes a boss (P-67).
- "Halve the next hit", Guard's subtitle, is now literally true.
- **The Storm Rat test is rewritten:** it asserted that the faster rat landed its announced Frenzy before a guard chosen that round, so only a guard raised the round before could blunt it. It now asserts that the guard chosen after the warning goes first and takes the Frenzy at a quarter. New tests: a slower player's guard halves a faster enemy's hit that round; an enemy's guard goes first; a guarded ordinary hit does not expose the Herald.
- Creep balance is unchanged (the bot never guards creeps; the Mire Slime guards once): median fights of 3 turns (the Barrow Skull 5), as before. The Herald was re-tuned for it (P-67).

**Why.** A telegraph is a promise that reacting to it works. With speed order the promise held only for players faster than the enemy, which in Eldmere is nobody.

**Amends** P-54 and P-57 (turn order, Guard's duration).

**Spec.** §4.5, §4.7.

---

## P-70 — Battle start: cold and warm, on the reference machine

**Problem.** §4.13's battle start budget (300 ms at 4× CPU throttle) failed on every nightly from its first run (#6): 707–1,288 ms on GitHub's runners, which draw WebGL in software, against 218–346 ms on a desktop with a GPU. One sample per run swung by ±20 % on the same commit, and nothing said which hardware the 300 ms was for, so the nightly stayed red and nobody was told.

**Decision.**
- Battle start is measured on **one cold battle** (the first after the page loads) and **seven warm ones**, from the `/api/world/encounter` response to the battle on screen. Each run reports cold, warm median and warm p95.
- The budgets apply to **cold** and to **warm p95** (`budgets.performance.battleStartColdMs` and `battleStartWarmP95Ms`, 300 ms each), never to one sample. They hold for Chrome at 4× CPU throttle on the **reference machine**: a desktop with a hardware GPU, the run before each release.
- On CI the nightly holds cold and warm p95 to within `maxRegression` (25 %) of the last good nightly's numbers, as P-38 does for frames, instead of the absolute budget. It is red only on a regression.
- A red nightly on `main` opens (or comments on) a `nightly-red` issue; the next green nightly closes it.

**Why.** The budget is about what a player feels, which a software-rendered runner cannot measure; what CI can measure reliably is whether a change made it worse. A check that is red every night teaches everyone to ignore it.

**Known limit.** Comparing with the last good nightly lets a slow drift of under 25 % a night pass; the pre-release run on the reference machine catches it.

**Amends** P-38 (battle start joins its regression check).

**Spec.** §4.13.
