# Shardbound — Algorithms

Every algorithm the game uses: the problem, where it runs, pseudocode, complexity and tests.
Sections marked ✍ are **student-implemented** (CLAUDE.md rule 8). Their pseudocode is deliberately left for the student to write after implementing them; only the contract is given here.

Last updated: after the Phase 1–8 code build (2026-11-18).

Notation: V = vertices (story nodes or quests), E = edges (choices / structural edges / prerequisite links), n = items in the relevant collection.

---

## 1. Breadth-first search ✍ — `ds/Graph.bfs`
- **Problem:** which story nodes can be reached from `p01` at all? (validator checks 1 and 4)
- **Contract:** returns reachable vertices in BFS visit order, each once; empty list for an unknown start.
- **Expected complexity:** time O(V+E), space O(V).
- **Tests:** `GraphTest.bfs*`, `StoryValidatorTest.check1UnreachableNode`.
- **Pseudocode / viva notes:** _to be written by the student after implementing._

## 2. Shortest path (BFS + parent map) ✍ — `ds/Graph.shortestPath`
- **Problem:** is the quick route at most 12 steps? (validator check 5) BFS finds fewest-edge paths because it explores in order of distance.
- **Contract:** vertices from `from` to `to` inclusive; `[from]` if equal; empty if unreachable.
- **Expected complexity:** time O(V+E), space O(V).
- **Tests:** `GraphTest.shortestPath*`, `StoryValidatorTest.check5QuickRouteTooLong`.
- **Pseudocode / viva notes:** _to be written by the student._

## 3. Iterative depth-first search ✍ — `ds/Graph.dfs`
- **Problem:** walk every reachable node to find non-ENDING nodes with no exit (validator check 2).
- **Contract:** reachable vertices in DFS order (neighbours in natural order), each once; no recursion.
- **Expected complexity:** time O(V+E), space O(V). An explicit stack avoids `StackOverflowError` on long chains (`GraphTest.dfsHandlesALongChainWithoutRecursion` uses 100 000 vertices).
- **Pseudocode / viva notes:** _to be written by the student._

## 4. Kahn's topological sort ✍ — `quest/QuestManager.topoOrder`
- **Problem:** show quests so prerequisites come first, and refuse content with circular prerequisites.
- **Contract:** valid order; ties in content order; `ContentValidationException` naming the quests left over when a cycle exists.
- **Expected complexity:** time O(V+E), space O(V+E).
- **Where it runs:** once at startup (`GameContent.prepareForPlay`); the quest log and Inspector use the cached order.
- **Tests:** `QuestManagerTest.topoOrder*`.
- **Pseudocode / viva notes:** _to be written by the student._

---

## 5. Structural graph construction (D10) — `story/StoryGraph`
```
for each node: addVertex(node)
for each choice (sorted by from_node, sort_order): addEdge(from, to, "choice <id>")
for each COMBAT node: addEdge(node, win, "win"); addEdge(node, lose, "lose"); if flee: addEdge(node, flee, "flee")
for each RESOLVER node: for each ending with via_resolver: addEdge(node, ending.node, "resolver")
```
Time O(V+E), space O(V+E). Without the extra edges, every COMBAT and RESOLVER node would look like a dead end. Test: `ContentFilesTest.structuralGraphHasCombatAndResolverEdges`.

## 6. Condition groups — OR of ANDs (D4) — `rules/ConditionGroup.test`
```
if no groups: return true
for each group in ascending grp:          // TreeMap
    if every condition in group passes: return true      // short-circuit AND
return false                                              // no group passed (OR)
```
Time O(c), space O(1). This is disjunctive normal form, which can express any boolean combination of the six condition kinds. Tests: `ConditionTest.sameGroupIsAndDifferentGroupsAreOr`.

## 7. Flag consistency (set differences, D12) — `StoryValidator.checkFlags`
```
read    = { key of every FLAG condition (choices and endings) }
written = { key of every SET_FLAG effect }   // '_' keys → error (D15)
warn for read − written ("read but never set")
warn for written − read ("set but never read")
```
Time O(R) for R condition/effect rows (HashSet/TreeSet operations). Test: `StoryValidatorTest.checks7And8FlagWarnings`.

## 8. Static quest-transition checks (D14) — `StoryValidator.checkQuestTransitions`
Collect `started` and `completed` quest-id sets from all effects; error if a quest is completed/failed but never started, or started while one of its prerequisites is never completed anywhere. Time O(R + Q·P).

## 9. Ending resolution (first match by priority) — `engine/EndingResolver.resolve`
```
for ending in resolver endings sorted by priority:
    if ending.conditions.test(state): return ending
throw (impossible if the last ending has no conditions)
```
Time O(e·c), space O(1). END_QUICK is excluded by `via_resolver = false` (D11). Test: `GameEngineTest.elderPathFoldsIntoTheRaidAndResolvesToTheWanderer`.

## 10. Quest state machine (D14) — `quest/QuestManager`
`start`: AVAILABLE→ACTIVE · `complete`: ACTIVE→COMPLETED, rewards, then `refreshAvailability` (LOCKED→AVAILABLE when every prerequisite is COMPLETED, O(V·P)) · `fail`: ACTIVE→FAILED. Any other transition logs a warning and changes nothing. Tests: `QuestManagerTest` (state-machine tests).

## 11. Turn order with a priority queue (S1) — `combat/CombatEngine.act`
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
Time O(n log n) per round (n ≤ 5), space O(n). Test: `CombatEngineTest.turnOrderIsFastestFirstPlayerWinsTies`, `winningGrantsXpAndGold`.

## 12. Damage formula — `combat/DamageCalculator.roll`
```
base = atk × tier.multiplier + weapon.attack    (no weapon: atk)
base = base × actionMultiplier                   (SKILL 1.8, boss heavy 1.6)
roll = base × (0.9 + nextDouble() × 0.2)
crit = nextInt(100) < critPct  → × 1.5
dmg  = max(1, round(roll × crit) − def × (defending ? 2 : 1))
```
O(1). The two random calls always happen in the same order, so a seeded `Random` gives the same fight every time. Tests: `DamageCalculatorTest` (exact values with a fixed `Random`, range and determinism with a seeded one).

## 13. Flee probability — `CombatEngine.fleeChance`
`clamp(50 + (playerSpd − avgEnemySpd) × 5, 10, 90)` percent; success if `rng.nextInt(100) < chance`. O(n). Test: `CombatEngineTest.fleeChanceIsClamped`.

## 14. Enemy AI strategies — `combat/ai/*`
Aggressive: always ATTACK. Defensive: DEFEND once when hp < 30% (`hp × 10 < maxHp × 3`, integer maths), else ATTACK. Boss: heavy attack every 3rd turn, every 2nd turn once hp ≤ 50% (I7). O(1) each.

## 15. Level-up and derived stats (D6, D28, D29) — `model/Stats`
```
maxHp = 40 + 5 × (level − 1)     atk = 6 + (level − 1)     xpToNext = 100 × level
addXp(a): xp += a; while xp ≥ xpToNext: xp −= xpToNext; level++; hp += 5 (capped)
```
O(L) for L levels gained. Tests: `StatsTest`, `MaxHpFormulaTest` (Java formula = SQL CHECK).

## 16. Seeded RNG reseeding (D7) — `GameState.reseedForSave`
`fresh = rng.nextLong(); rngSeed = fresh; rng = new Random(fresh)`. After this, the live game and a save made now produce identical rolls. O(1). Test: `GameStateTest.reseedMakesLiveAndSavedSequencesIdentical`.

## 17. Autosave snapshot (D24) — `GameEngine.autosave`
Under the state's lock: reseed the live state → `deepCopy()` (O(n), without bus/combat/toasts) → submit to a single-thread executor. The copy is never touched by request threads again, so the background write needs no lock.

## 18. Save transaction ✍ — `persistence/SaveRepository.save`
- **Contract:** spec §4.4; all-or-nothing; crash simulation after the inventory insert.
- **Expected complexity:** O(n) rows in a constant number of batches.
- **Tests:** `SaveRepositoryIT` (round trip, version + audit trigger, rollback keeps the old save).
- **Pseudocode / viva notes:** _to be written by the student._

## 19. Content import (upsert + guarded delete, D1) — `tools/ContentImporter.importAll`
```
BEGIN
  upsert every table on its natural key (choices: ON CONFLICT (from_node, sort_order) RETURNING choice_id)
  replace owned child rows (conditions, effects, prereqs, encounter enemies, ending conditions)
  for each table, child before parent:
      for each id in DB but not in JSON:
          if a save/player references it → ROLLBACK with "row X … save #N (player, slot)"
          DELETE it
COMMIT   (deferred FKs between nodes are checked here)
```
Time O(total rows). Needs PostgreSQL to run.

## 20. Inventory ordering — `item/Inventory.weaponsByTier`
Collect weapons (O(n)), sort with `Comparator.comparing(tier, BY_RANK_DESC).thenComparing(attack desc)` — O(n log n) (TimSort, stable). Test: `InventoryTest.weaponsByTierThenAttack`.
