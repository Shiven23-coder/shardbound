# Shardbound — Decision Log

Why the spec says what it says. `docs/PROJECT_SPEC.md` is the source of truth for **what** to build; this file records **why** each post-draft change was made. Every decision is tagged in the spec as **(Dn)** or **(Sn)** so you can jump between the two.

- **D1–D19** came from the Phase 0 spec review (contradictions and gaps).
- **S1–S9** are the smaller points approved at the same time.
- **D20–D32** came from the review of the ER and class diagrams.
- Status: all accepted on 2026-11-18, before Phase 1. Where a later decision changes an earlier one, the earlier entry says so.

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
| S5 | Vite dev server proxies `/api` to `localhost:7070`. | One origin in the browser, so no CORS configuration and the `X-Session` header passes untouched. | §2, §3 |
| S6 | `SaveRepositoryIT` runs on a separate `shardbound_test` database via the failsafe plugin (`mvnw verify`). | Integration tests must not touch real saves; `mvnw test` stays fast and DB-free. | §3, §4.4, §10 |
| S7 | `insertHistory` walks the history stack with `descendingIterator()` so `step_no` 1 is the oldest step. | `ArrayDeque` used as a stack iterates newest-first; without this the journal loads reversed. | §4.1, §4.4 |
| S8 | Phase 2 cannot go green until the student-written `Graph.bfs/dfs/shortestPath` exists, because `StoryValidator` uses it. | Makes the dependency explicit in the phase plan. | §10 |
| S9 | Language level Java 21 via `maven.compiler.release=21`; the installed JDK 25 is fine. | `--release 21` rejects any API added after 21, so the code runs on any Java 21 machine. | §2 |
