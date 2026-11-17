# Shardbound — Algorithms

Every algorithm the game uses, with pseudocode, complexity, where it runs and how it is tested.
**Skeleton:** each section is filled in at the end of the phase that implements it. Sections for student-implemented code (marked ✍) are written *after* the student's implementation, describing their code.

Last updated: Phase 0 (structure only).

Template for each section:
- **Problem** — what question the algorithm answers in the game
- **Where** — class and method
- **Pseudocode**
- **Complexity** — time and space, with V, E, n defined
- **Why this algorithm** — and what the alternative would cost
- **Tests** — test class and the cases covered
- **Viva notes** — likely questions

---

## 1. Breadth-first search ✍ — Phase 2
`ds/Graph.bfs`. Used by `StoryValidator` checks 1 and 4 (reachability).

## 2. Shortest path (unweighted, BFS + parent map) ✍ — Phase 2
`ds/Graph.shortestPath`. Used by validator check 5 (`p01` → `END_QUICK` ≤ 12).

## 3. Iterative depth-first search ✍ — Phase 2
`ds/Graph.dfs`. Used by validator check 2 (dead ends).

## 4. Condition groups (OR of ANDs) — Phase 2
`rules/ConditionGroup.test` and `describe` (D4).

## 5. Flag consistency (set differences) — Phase 2
`StoryValidator` checks 7–8 (D12).

## 6. Ending resolution (first match by priority) — Phase 8
`engine/EndingResolver.resolve`.

## 7. Kahn's topological sort ✍ — Phase 6
`quest/QuestManager.topoOrder`. Quest log order and cycle detection.

## 8. Quest state machine — Phase 6
`quest/QuestManager` transitions and LOCKED → AVAILABLE re-evaluation (D14).

## 9. Turn order with a priority queue — Phase 5
`combat/CombatEngine` round loop; tie-break rule (S1).

## 10. Damage formula — Phase 5
`combat/DamageCalculator` (spec §6.6).

## 11. Flee probability — Phase 5
`combat/CombatEngine` (spec §6.6, D9).

## 12. Enemy AI strategies — Phase 5
`combat/ai/AggressiveAI`, `DefensiveAI`, `BossAI`.

## 13. Level-up and derived stats — Phase 2/4
`model/Stats` (D6).

## 14. Seeded RNG and reseeding at save ✍ (part of save) — Phase 7
`GameState.reseedForSave` (D7).

## 15. Save transaction ✍ — Phase 7
`persistence/SaveRepository.save`.

## 16. Content import (upsert + guarded delete) — Phase 2
`tools/ContentImporter` (D1).

## 17. Inventory ordering (`weaponsByTier`) — Phase 4
`item/Inventory` with `Comparator` on `Tier` then attack.
