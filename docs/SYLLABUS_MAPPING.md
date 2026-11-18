# Shardbound — Syllabus Mapping

Where each graded concept lives in the code, SQL and tests. Paths are under `backend/src/main/java/com/shardbound/` unless they start with `database/`, `content/` or `frontend/`.

Last updated: after the Phase 1–8 code build (2026-11-18).
**Status key:** ✅ code + green test · ✍ student-implemented, tests written and failing until done · 🗄 needs PostgreSQL to demonstrate

---

## 1. Java-2

| Concept | Class / file | What to show | Test | Status |
|---|---|---|---|---|
| Inheritance / abstract classes | `item/Item` → `Weapon`, `Consumable`, `KeyItem`; `combat/Combatant` → `PlayerCombatant`, `Enemy` | `Combatant` declares abstract `hp()/atk()/setHp()`; the player's hp lives in `Stats`, an enemy's in a field | `InventoryTest`, `CombatEngineTest` | ✅ |
| Interfaces / polymorphism | `rules/Condition`, `rules/Effect`, `combat/ai/EnemyAI`, `event/GameListener` | `for (Effect e : choice.effects()) e.apply(s, bus)` — 11 effect classes, one call | `ConditionTest` | ✅ |
| Sealed interface | `rules/Condition` (`permits` the 6 condition records) | compiler knows every kind | `ConditionTest` | ✅ |
| Records | all DTOs in `api/dto`, conditions, effects, `Quest`, `Ending`, `Step`, `EnemyTemplate`, `RawContent.*` | immutable data with compact constructors (`List.copyOf`) | `JsonContractTest` | ✅ |
| Switch expressions | `RuleFactory.condition/effect`, `GameEngine.enterNode`, `GameEngine.combatAction`, `Op.compare` | exhaustive switch on `NodeType` / `CombatOutcome` | — | ✅ |
| Generics | `ds/Graph<V,E>`, `ds/Edge<V,E>`, `item/ItemStack<T extends Item>`, `ContentImporter.idsOf<T>` | bounded type parameter on ItemStack | `GraphTest` | ✅ |
| Enums with fields/methods | `model/Tier` (multiplier, comparator), `model/Faction` (displayName), `rules/Op` (symbol, compare), `quest/QuestStatus`, `event/EventType` | enum order matches SQL enums | `QuestStatusOrderTest` | ✅ |
| Custom exceptions | `exception/*` (8 classes under `GameException`) | each maps to an HTTP status in `api/ErrorHandler.statusFor` | `JsonContractTest` | ✅ |
| Collections + streams | `HashMap`, `LinkedHashMap`, `EnumMap`, `TreeMap`, `ArrayDeque`, `PriorityQueue`, `HashSet`, `ConcurrentHashMap` (see §2) | — | — | ✅ |
| Encapsulation | `engine/GameState`, `combat/CombatState` (D30) | private fields, intent methods, unmodifiable views, package-private raw access | `GameStateTest.viewsAreReadOnly` | ✅ |
| Multithreading | `engine/GameEngine` (`synchronized (s)`, single-thread autosave `ExecutorService`), `engine/SessionStore` (`ConcurrentHashMap`), `persistence/SqlLog` (`synchronized`) | autosave: reseed live → deep copy → submit (D24) | `GameStateTest.deepCopy…` | ✅ |
| JDBC | `persistence/*Repository`, `tools/ContentImporter` | `PreparedStatement`, `addBatch/executeBatch`, `setAutoCommit(false)/commit/rollback`, `RETURNING`, enum casts `?::weapon_tier` | `SaveRepositoryIT` | 🗄 ✍ |
| File I/O | `content/JsonContentReader` (JSON in), `tools/GraphExporter` (DOT out), tests reading `database/01_schema.sql` | Jackson tree model; `PrintWriter` | `ContentFilesTest`, `MaxHpFormulaTest` | ✅ |
| Design patterns | Observer (`EventBus` + `GameListener`), Strategy (`EnemyAI`, `AchievementRule`), Factory (`RuleFactory`, `EnemyAI.forType`), Repository (`persistence/*`), State machine (`Mode`, `QuestStatus`), Builder (`ConditionGroup.Builder`), Flyweight (`EnemyTemplate` → `Enemy`, D20) | — | `AchievementManagerTest` | ✅ |

## 2. Data Structures

| Structure / algorithm | Class / file | Why this structure | Complexity | Test | Status |
|---|---|---|---|---|---|
| `HashMap<String, StoryNode>` | `story/StoryGraph.nodes` | O(1) lookup by id on every request | O(1) | `ContentFilesTest` | ✅ |
| Adjacency list `Graph<V,E>` | `ds/Graph` (`HashMap<V, List<Edge>>`) | story graph is sparse (1–4 exits/node) | O(V+E) memory | `GraphTest` | ✅ |
| BFS (reachability) | `ds/Graph.bfs` → `StoryValidator` checks 1, 4 | level order, `ArrayDeque` queue | O(V+E) | `GraphTest`, `StoryValidatorTest` | ✍ |
| BFS + parent map (shortest path) | `ds/Graph.shortestPath` → check 5 | fewest edges in an unweighted graph | O(V+E) | `GraphTest` | ✍ |
| Iterative DFS (dead ends) | `ds/Graph.dfs` → check 2 | explicit stack, no recursion depth limit | O(V+E) | `GraphTest.dfsHandlesALongChain…` | ✍ |
| `LinkedHashMap` inventory, capacity 20 | `item/Inventory` | O(1) add/remove, stable display order | O(1); sort O(n log n) | `InventoryTest` | ✅ |
| `enum Tier` + `Comparator` | `model/Tier.BY_RANK_DESC`, `Inventory.BY_TIER_THEN_ATTACK` | same order as `weapon_tier` | O(1) per compare | `InventoryTest.weaponsByTier…` | ✅ |
| `EnumMap<Faction,Integer>` | `GameState.reputation` | 4 fixed keys, array-backed | O(1) | `ConditionTest` | ✅ |
| `EnumMap<EventType, List<AchievementRule>>` | `AchievementManager.RULES` (static, D23) | only rules for this event type are checked | O(r) per event | `AchievementManagerTest` | ✅ |
| `TreeMap<Integer, List<Condition>>` | `rules/ConditionGroup` (D4) | groups in grp order even with gaps | O(c) test | `ConditionTest.groupsAreDescribed…` | ✅ |
| Kahn's topological sort | `quest/QuestManager.topoOrder` | order + cycle detection | O(V+E) | `QuestManagerTest` | ✍ |
| `PriorityQueue<Combatant>` | `combat/CombatEngine.act` + `TURN_ORDER` | fastest acts first; ties: player, then index (S1) | O(n log n) per round | `CombatEngineTest.turnOrder…` | ✅ |
| Bounded `ArrayDeque` (last 8 / last 5) | `CombatState.log`, `persistence/SqlLog` | O(1) add at tail, drop at head | O(1) | `CombatEngineTest.logKeeps…` | ✅ |
| `ArrayDeque` as stack / FIFO | `GameState.history` (stack), `GameState.pendingToasts` (queue) | journal newest-first; toasts in order | O(1) | `GameStateTest.history…` | ✅ |
| `HashSet` | `AchievementManager.unlocked`; validator id sets and flag set differences (D12) | O(1) contains | O(1) | `StoryValidatorTest.checks7And8` | ✅/✍ |
| `ConcurrentHashMap` | `engine/SessionStore` | many Jetty threads | O(1) | — | ✅ |

## 3. DBMS

| Concept | SQL file / object | What to show | Status |
|---|---|---|---|
| Normalization (3NF, no exceptions) | `database/01_schema.sql` `save_slot` | max_hp/atk/def/spd derived, CHECK computes the bound (D6, D28); xp is within-level (D29) | ✅ |
| ISA / generalization | `item` → `weapon`, `consumable` (shared PK, CASCADE) | one LEFT JOIN in `ContentRepository.loadItems` | ✅ |
| Keys | composite PKs (`encounter_enemy`, `choice_condition`, `save_*`), natural key `UNIQUE(from_node, sort_order)`, `UNIQUE(player_id, slot_no)` | — | ✅ |
| CHECK constraints | combat node shape, flee only on COMBAT, region list, hp bound by level, value ranges | — | ✅ |
| ENUM types and ordering | `weapon_tier`, `quest_status` | `fn_best_weapon` uses `ORDER BY tier DESC` | ✅ |
| Deferrable constraints | `win_node`, `lose_node`, `flee_node`, `choice.to_node` | forward references inside one import transaction | 🗄 |
| Referential actions | CASCADE (owned rows), RESTRICT (player → content), SET NULL (`save_history.choice_id`) | table in `docs/diagrams/er-diagram.md` §3 | ✅ |
| Transactions / ACID | `ContentImporter.importAll` (one transaction, rollback on any error); `SaveRepository.save` ✍ | crash simulation proves atomicity | 🗄 ✍ |
| Upsert | `ON CONFLICT … DO UPDATE` in importer, `SaveRepository.UPSERT_SLOT`, `PlayerRepository` (DO UPDATE so RETURNING works), `MetaRepository.recordEnding` (`LEAST`) | — | 🗄 |
| Triggers | `02_functions_triggers.sql`: `touch_save` (BEFORE), `audit_save` (AFTER), `all_endings` | `SaveRepositoryIT.overwritingBumpsVersionAndAudits` | 🗄 |
| Functions | `fn_best_weapon(save_id)` | `SELECT * FROM fn_best_weapon(1);` | 🗄 |
| Views | `v_leaderboard` (COUNT DISTINCT across two LEFT JOINs) | `MetaRepository.leaderboard` | 🗄 |
| Indexes | `one_equipped_per_save` (partial unique), `idx_choice_from`, `idx_history_save` | — | 🗄 |
| SQL injection prevention | every query is a `PreparedStatement` with `?` | the only concatenated SQL is table/column names from constants in `ContentImporter.deleteMissing` | ✅ |

## 4. Spec traceability

| Spec section | Main files | Tests | Status |
|---|---|---|---|
| §4 Database | `database/01–03, 99` | `QuestStatusOrderTest`, `MaxHpFormulaTest`, `SaveRepositoryIT` | written, 🗄 not yet run on PostgreSQL |
| §5 Data structures | see §2 above | — | ✅ / ✍ |
| §6.2 Conditions / effects | `rules/*` | `ConditionTest` | ✅ |
| §6.3 Choose flow | `engine/GameEngine.choose/enterNode` | `GameEngineTest` | ✍ (needs topoOrder) |
| §6.4 Inventory | `item/*` | `InventoryTest` | ✅ |
| §6.5 Quests | `quest/QuestManager` | `QuestManagerTest` | ✅ state machine / ✍ topoOrder |
| §6.6 Combat | `combat/*` | `DamageCalculatorTest`, `CombatEngineTest` | ✅ |
| §6.8 Achievements | `achievement/*`, `event/*` | `AchievementManagerTest` | ✅ |
| §6.9 Endings | `engine/EndingResolver`, `GameEngine.finish` | `GameEngineTest.elderPath…` | ✍ (needs topoOrder) |
| §6.10 Save / load | `GameEngine.save/load/autosave`, `persistence/SaveRepository` | `SaveRepositoryIT` | ✍ 🗄 |
| §8.3 Importer | `tools/ContentImporter` | `SaveRepositoryIT` setup | 🗄 |
| §8.4 Validator | `story/StoryValidator` | `StoryValidatorTest` | ✍ (needs Graph) |
| §9 API / frontend | `api/*`, `frontend/src/*` | `JsonContractTest`, `npm run build` | ✅ build; 🗄 not run end-to-end |
