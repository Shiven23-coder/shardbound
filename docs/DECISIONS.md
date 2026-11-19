# Shardbound — Decision Log

Why the specs say what they say. `docs/V2_SPEC.md` is the source of truth for **what** to build (`docs/PROJECT_SPEC.md` is the historical v1 spec); this file records **why** each change was made. Decisions are tagged in the specs as **(Dn)**, **(Sn)**, **(V-nn)** or **(P-nn)** so you can jump between them.

- **D1–D19** came from the v1 spec review (contradictions and gaps).
- **S1–S9** are the smaller points approved at the same time.
- **D20–D32** came from the review of the v1 ER and class diagrams.
- **I1–I12** are implementation choices made while building v1.
- **V-01 … V-35** are the v2 design decisions.
- **P-01 … P-28** are the platform reset (Phase P0) and its follow-ups: the project became a public game, and these entries win over anything older.
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

## Platform reset decisions (P-01 … P-28)

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

**Amended by P-27** (Whispering Wood placed after V6).

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

**Decision.** Annotated tag `v1-archive` on `main` at `2a17e8a` ("v1 archived; never verified on PostgreSQL"). The PostgreSQL run is skipped. The deleted study docs are preserved in the tag.

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

---

## P-28 — CI guards content ids against unmapped removal

**Problem.** Saves and active runs store content ids (P-05). Renaming or removing an id in content makes those saves fail with 409 `SAVE_INCOMPATIBLE` on load, which a player only discovers after a deploy.

**Decision.** From V2c, next to the `oasdiff` check, CI compares every content id with the last release's content and fails if an id is removed or renamed without a `SaveUpgrade` that maps it. The 409 on load stays as the last line of defence.

**Why.** It moves the failure from a player's load screen to the pull request that caused it, the same way `oasdiff` guards the API.

**Spec.** §4.12, §12 (V2c).
