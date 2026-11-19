# Shardbound — Algorithms

The notable algorithms in the game: the problem each one solves, where it runs, its contract, complexity and tests.

Notation: V = vertices (story nodes, areas or quests), E = edges, n = items in the relevant collection.

---

## 1. Breadth-first search — `ds/Graph.bfs`
- **Problem:** which story nodes can be reached from `p01` at all (validator checks 1 and 4)? From V3 the same idea runs on the walkable tiles of a map: reachability checks for world events (V2_SPEC §4.11), the map validator and the playtest bot.
- **Contract:** returns reachable vertices in BFS visit order, each once; empty list for an unknown start.
- **Complexity:** time O(V+E), space O(V). On a W×H tile grid, O(W·H) in the worst case.
- **Tests:** `GraphTest.bfs*`, `StoryValidatorTest.check1UnreachableNode`.

## 2. Shortest path (BFS + parent map) — `ds/Graph.shortestPath`
- **Problem:** is the quick route at most 12 steps (validator check 5)? BFS finds fewest-edge paths because it explores in order of distance. The world-event speed check (P-19) uses the same fewest-tile path length.
- **Contract:** vertices from `from` to `to` inclusive; `[from]` if equal; empty if unreachable.
- **Complexity:** time O(V+E), space O(V).
- **Tests:** `GraphTest.shortestPath*`, `StoryValidatorTest.check5QuickRouteTooLong`.

## 3. Iterative depth-first search — `ds/Graph.dfs`
- **Problem:** walk every reachable node to find non-ENDING nodes with no exit (validator check 2).
- **Contract:** reachable vertices in DFS order (neighbours in natural order), each once; no recursion.
- **Complexity:** time O(V+E), space O(V). An explicit stack avoids `StackOverflowError` on long chains (`GraphTest.dfsHandlesALongChainWithoutRecursion` uses 100 000 vertices).

## 4. Dijkstra's shortest paths — `ds/Graph.dijkstra`
- **Problem:** least total weight between areas on the weighted area graph: fast travel, quest next hop, bot routing and region preloading (V-17).
- **Contract:** distances and parents for every reachable vertex; ties keep the first-found parent; negative weights → `IllegalArgumentException`; `ShortestPaths.pathTo` rebuilds a path.
- **Complexity:** O((V+E) log V) time, O(V+E) space (lazy-deletion `PriorityQueue`).
- **Tests:** `DijkstraTest`.

## 5. Kahn's topological sort — `quest/QuestManager.topoOrder`
- **Problem:** show quests so prerequisites come first, and refuse content with circular prerequisites.
- **Contract:** valid order; ties in content order; `ContentValidationException` naming the quests left over when a cycle exists.
- **Complexity:** time O(V+E), space O(V+E).
- **Where it runs:** once at startup (`GameContent.prepareForPlay`), so a quest cycle stops startup; the quest log uses the cached order.
- **Tests:** `QuestManagerTest.topoOrder*`.

---

## 6. Condition groups — OR of ANDs (D4) — `rules/ConditionGroup.test`
```
if no groups: return true
for each group in ascending grp:          // TreeMap
    if every condition in group passes: return true      // short-circuit AND
return false                                              // no group passed (OR)
```
Time O(c), space O(1). This is disjunctive normal form, which can express any boolean combination of the condition kinds. Choices, dialogue options and endings share this one evaluator. Tests: `ConditionTest.sameGroupIsAndDifferentGroupsAreOr`.

## 7. Turn order with a priority queue (S1) — `combat/CombatEngine.act`
```
queue = PriorityQueue(TURN_ORDER: speed desc, player before enemies, then enemy index)
add player and every living enemy
while queue not empty:
    actor = poll(); skip if dead
    actor.defending = false                   // DEFEND lasts until the actor's next turn
    player → apply chosen action (FLEE success ends the round as FLED)
    enemy  → apply EnemyAI.decide(...)
    if all enemies dead → WON; if player dead → LOST
return ONGOING
```
Time O(n log n) per round (n ≤ 5), space O(n). Tests: `CombatEngineTest.turnOrderIsFastestFirstPlayerWinsTies`, `winningGrantsXpAndGold`.

## 8. Damage formula — `combat/DamageCalculator.roll`
v1 formula (the v2 ratio formula is in V2_SPEC §4.7):
```
base = atk × tier.multiplier + weapon.attack    (no weapon: atk)
base = base × actionMultiplier                   (SKILL 1.8, boss heavy 1.6)
roll = base × (0.9 + nextDouble() × 0.2)
crit = nextInt(100) < critPct  → × 1.5
dmg  = max(1, round(roll × crit) − def × (defending ? 2 : 1))
```
O(1). The random calls always happen in the same order, so a seeded `Random` gives the same fight every time. Tests: `DamageCalculatorTest` (exact values with a fixed `Random`, range and determinism with a seeded one).

## 9. Seeded RNG reseeding (D7) — `GameState.reseedForSave`
`fresh = rng.nextLong(); rngSeed = fresh; rng = new Random(fresh)`. After this, the live game and a snapshot taken now produce identical rolls. O(1). Test: `GameStateTest.reseedMakesLiveAndSavedSequencesIdentical`.

## 10. Autosave snapshot (D24) — `GameEngine.autosave`
Under the state's lock: reseed the live state → take a snapshot (O(n), without the event bus and UI fields) → hand it to the writer. The snapshot is never touched by request threads again, so the write needs no lock.

## 11. Save snapshots and active runs (P-05, P-22)
- **Save slots:** a `SaveSnapshot` is serialised to one JSONB document and written with its summary columns (`level`, `area_id`, `playtime_seconds`) in a single UPSERT. On load, `SaveUpgrader` applies every upgrade above the stored `save_version` in key order (`SortedMap.tailMap`) and writes the result back in the same transaction. O(document size + number of upgrades).
- **Active runs:** after every state-changing action, inside the per-game lock, the run is written through with optimistic locking:
  ```
  UPDATE active_run SET data = ?, version = version + 1, updated_at = now()
   WHERE run_id = ? AND version = ?
  0 rows → 409 RUN_CONFLICT (the client refetches /api/game)
  ```
  The in-memory state is only a cache; a miss rebuilds the run from `active_run`. Because the RNG is reseeded on each write (D7), a reloaded run continues with the same rolls. Test: `ActiveRunIT` (restart mid-combat, identical state, stale version → 409).
