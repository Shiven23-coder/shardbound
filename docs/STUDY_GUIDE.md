# Shardbound — Study Guide (viva algorithms)

One section per algorithm in CLAUDE.md rule 8. Each has: the idea in 3 lines, a dry run with the data structure's contents at every step, complexity and why, common mistakes, and 5 viva questions with answers.
Code: `backend/src/main/java/com/shardbound/…` (the step numbers in the code comments match the steps below). Tests named in each section must stay green.

Contents: [1 BFS](#1-breadth-first-search--graphbfs) · [2 DFS](#2-iterative-depth-first-search--graphdfs) · [3 Shortest path](#3-shortest-path-bfs--parent-map--graphshortestpath) · [4 Kahn's topological sort](#4-kahns-topological-sort--questmanagertopoorder) · [5 Save transaction](#5-the-save-transaction--saverepositorysave)

**Example graph used in §1–§3** (the one in `GraphTest`):
```
A → B,  A → C,  B → D,  C → D,  C → E,  D → F,  F → B,  G → A
neighbors(A) = [B, C]   neighbors(C) = [D, E]   E has no exits   G is not reachable from A   B → D → F → B is a cycle
```

---

## 1. Breadth-first search — `Graph.bfs`

**Idea**
1. Explore the graph in "rings": first the start, then everything 1 edge away, then 2 edges away, …
2. A FIFO queue holds discovered-but-not-yet-expanded vertices; the oldest is expanded first.
3. A visited set marks a vertex when it is *discovered*, so it is queued at most once.

**Dry run: `bfs("A")`**

| Step | Poll | Neighbours examined | Newly discovered | Queue after (head → tail) | Visited after | Order |
|---|---|---|---|---|---|---|
| init | — | — | A | [A] | {A} | [] |
| 1 | A | B, C | B, C | [B, C] | {A,B,C} | [A] |
| 2 | B | D | D | [C, D] | {A,B,C,D} | [A,B] |
| 3 | C | D (seen), E | E | [D, E] | {A,B,C,D,E} | [A,B,C] |
| 4 | D | F | F | [E, F] | {A,…,F} | [A,B,C,D] |
| 5 | E | — | — | [F] | same | [A,B,C,D,E] |
| 6 | F | B (seen) | — | [] | same | [A,B,C,D,E,F] |

Result `[A, B, C, D, E, F]`; G is never reached (`GraphTest.bfsVisitsLevelByLevel`, `bfsDoesNotReachVerticesWithNoPathFromStart`).

**Complexity: O(V + E) time, O(V) space**
- Every vertex enters the queue at most once, because it is marked on discovery. Polling it and listing its neighbours is O(1) + O(its out-degree).
- Summed over all vertices, the out-degrees add up to E.
- Space: the visited set and the queue each hold at most V vertices.

**Common mistakes**
- Marking visited when *polled* instead of when *queued*: a vertex can then be queued many times (more memory, duplicates).
- Using a stack (`push/pop`) by accident. That is DFS, and the level order is lost.
- Forgetting the unknown-start case. It returns `[]`; it must not throw.
- Recursion: BFS is naturally iterative.

**Viva questions**
1. *Why does BFS use a queue?* — FIFO guarantees every vertex at distance d is expanded before any at d + 1, which is exactly the "ring" order.
2. *Why mark visited on discovery?* — Otherwise C would queue D again even though B already queued it. With the mark, each vertex is queued once, which gives the O(V) queue bound.
3. *Where does Shardbound use BFS?* — `StoryValidator` checks 1 and 4: nodes and endings unreachable from `p01`, on the structural graph, which includes COMBAT win/lose/flee and RESOLVER edges (D10).
4. *Complexity with an adjacency matrix instead of a list?* — O(V²): each expansion scans a whole row of V cells. The story graph is sparse (1–4 exits per node), so the adjacency list is better.
5. *What happens with the cycle B → D → F → B?* — When F is expanded, B is already visited, so it is skipped. Cycles never cause infinite loops.

---

## 2. Iterative depth-first search — `Graph.dfs`

**Idea**
1. Go as deep as possible along one branch before backtracking.
2. An explicit LIFO stack replaces recursion, so very deep graphs cannot overflow the call stack.
3. A vertex is "visited" when *popped*. Neighbours are pushed in reverse so the first neighbour is explored first.

**Dry run: `dfs("A")`** (stack shown top → bottom)

| Step | Pop | Already visited? | Push (reverse order, unvisited only) | Stack after | Order |
|---|---|---|---|---|---|
| init | — | — | A | [A] | [] |
| 1 | A | no | C, then B | [B, C] | [A] |
| 2 | B | no | D | [D, C] | [A,B] |
| 3 | D | no | F | [F, C] | [A,B,D] |
| 4 | F | no | (B is visited → nothing) | [C] | [A,B,D,F] |
| 5 | C | no | E (D is visited) | [E] | [A,B,D,F,C] |
| 6 | E | no | — | [] | [A,B,D,F,C,E] |

Result `[A, B, D, F, C, E]` (`GraphTest.dfsGoesDeepFirst`).

**Complexity: O(V + E) time, O(V + E) worst-case space**
- Each vertex is expanded once (the visited check after pop), and each edge causes at most one push.
- The stack can hold a vertex more than once before its first pop (pushed by several neighbours), so in the worst case it holds O(E) entries.
- A 100 000-vertex chain works (`dfsHandlesALongChainWithoutRecursion`); recursion would throw `StackOverflowError`.

**Common mistakes**
- Pushing neighbours in natural order: the *last* neighbour is then explored first, giving `[A, C, E, D, F, B]`.
- Marking visited on push: then the order stops matching a recursive DFS.
- Forgetting the "skip if already visited after pop" check: vertices get expanded twice.
- Writing it recursively: the call stack overflows on long chains.

**Viva questions**
1. *Why not recursion?* — Java's call stack is limited (~10k frames by default). A long chain of story nodes would overflow it; a heap-allocated `ArrayDeque` grows as needed.
2. *Why push in reverse order?* — The stack is LIFO. Reverse pushing puts the first neighbour on top, so the visit order equals the recursive version.
3. *Why can the stack hold O(E) entries?* — With "visited on pop", a vertex such as D can be pushed by B and by C before it is popped. Each push is caused by one edge, so the total is ≤ E.
4. *Where is DFS used?* — `StoryValidator` check 2: walk the reachable nodes and report non-ENDING nodes without structural exits (dead ends).
5. *BFS or DFS for reachability?* — Both visit exactly the reachable set in O(V + E). The order differs, which matters only for shortest paths (BFS) or deep-first exploration.

---

## 3. Shortest path (BFS + parent map) — `Graph.shortestPath`

**Idea**
1. In an unweighted graph, BFS reaches every vertex first along a fewest-edges route.
2. Record, for each discovered vertex, which vertex discovered it (the parent map).
3. When the target is discovered, walk the parents back to the start and reverse the list.

**Dry run: `shortestPath("A", "F")`**

| Step | Poll | Newly discovered → parent | Queue after | Parent map after |
|---|---|---|---|---|
| init | — | A → null | [A] | {A:null} |
| 1 | A | B → A, C → A | [B, C] | {A:–, B:A, C:A} |
| 2 | B | D → B | [C, D] | + D:B |
| 3 | C | E → C (D already known) | [D, E] | + E:C |
| 4 | D | F → D | [E, F] | + F:D → **F found, loop stops** |

Walk back: F → D → B → A, then reversed: **`[A, B, D, F]`**, 3 edges.
The equally short A → C → D → F is not chosen, because D was discovered from B first (`shortestPathPrefersFirstDiscoveredRoute`).

**Complexity: O(V + E) time, O(V) space**
- This is BFS (each vertex is discovered once), plus a walk-back of at most V steps.
- The parent map and the queue each hold ≤ V entries.
- Stopping early when the target is found only makes it faster.

**Common mistakes**
- Using DFS: it finds *a* path, not the shortest.
- Not handling `from == to`: the answer is `[A]`, 0 edges.
- Returning the path in reverse order: forgetting `Collections.reverse`.
- Using `parent.get(v) == null` as "not visited". The start's parent is null too; use `containsKey`.

**Viva questions**
1. *Why is the first discovery of the target the shortest?* — BFS discovers vertices in non-decreasing distance order. Anything discovered later is at least as far.
2. *Why does this fail for weighted graphs?* — Fewest edges ≠ least total weight. That needs Dijkstra (V2, `Graph.dijkstra`).
3. *What does the parent map contain?* — For each discovered vertex, the vertex it was discovered from: a BFS tree. Following parents from any vertex leads back to the start.
4. *How is it used in Shardbound?* — Validator check 5: the quick route `p01 → END_QUICK` must take ≤ 12 steps. It is 10.
5. *Complexity of rebuilding the path?* — O(path length) ≤ O(V): one map lookup per step, plus the reverse.

---

## 4. Kahn's topological sort — `QuestManager.topoOrder`

**Idea**
1. A quest may appear only after all its prerequisites. Count, for each quest, how many prerequisites are still missing (its in-degree).
2. Repeatedly output a quest with count 0, and decrease the count of every quest that depends on it.
3. If some quests are never output, their counts never reached 0: they are in a cycle (or depend on one).

**Dry run: the shard chain** (content order: `edge(one,two,three)`, `three(two)`, `two(one)`, `one()`)

Reversed edges built in content order: `one → [edge, two]`, `two → [edge, three]`, `three → [edge]`.

| Step | Poll | Output | In-degree after (edge, three, two, one) | Became 0 → added to queue | Queue after |
|---|---|---|---|---|---|
| init | — | [] | (3, 1, 1, 0) | one | [one] |
| 1 | one | [one] | (2, 1, **0**, –) | two | [two] |
| 2 | two | [one, two] | (1, **0**, –, –) | three | [three] |
| 3 | three | [one, two, three] | (**0**, –, –, –) | edge | [edge] |
| 4 | edge | [one, two, three, edge] | — | — | [] |

4 of 4 output → valid order `[one, two, three, edge]` (`QuestManagerTest.topoOrderPutsPrerequisitesFirst`).

**Cycle: `a(c)`, `b(a)`, `c(b)`, `free()`**
- The in-degrees start at a 1, b 1, c 1, free 0, so the queue starts as [free].
- After `free` is output the queue is empty, with only 1 of 4 output.
- a, b and c still have in-degree 1, so `ContentValidationException("…cycle", [a, b, c])` is thrown (`topoOrderDetectsACycle`).

**Complexity: O(V + E) time and space**
- Building the in-degrees is O(V).
- Building the reversed lists is O(E) (one entry per prerequisite link).
- Each quest enters the queue once (O(V)), and each link is decremented exactly once, when its prerequisite is output (O(E)).
- Space: the in-degree map, the reversed lists and the queue.

**Common mistakes**
- Building edges the wrong way round: decrementing the prerequisites of the output quest instead of its dependants.
- Scanning all quests on every step to find in-degree 0: O(V²) instead of O(V + E).
- Not detecting cycles: returning a partial order silently.
- A plain `HashMap` for the in-degrees: the order becomes random between runs. `LinkedHashMap` keeps content order.

**Viva questions**
1. *What is a topological order?* — An ordering of a DAG's vertices where every edge u → v puts u before v. Here: prerequisites before quests.
2. *How does Kahn's algorithm detect a cycle?* — Vertices on a cycle each wait for another vertex on the same cycle, so none ever reaches in-degree 0. The output is shorter than V.
3. *Why a queue? Would a stack work?* — Any container of in-degree-0 vertices gives a valid order. The FIFO queue plus content order makes the order deterministic and close to the authored order, which the quest log shows.
4. *When does it run?* — Once at startup (`GameContent.prepareForPlay`). A cyclic content file stops the server instead of breaking a game.
5. *Kahn vs DFS-based topological sort?* — Both are O(V + E). DFS outputs vertices in reverse post-order and finds cycles via "on the current path" marks. Kahn's is iterative and reports the stuck vertices directly.

---

## 5. The save transaction — `SaveRepository.save`

**Idea**
1. A save is many rows in seven tables; it must be written **all or nothing** (atomicity).
2. Turn off auto-commit, upsert the slot, replace the child rows in batches, then `commit()`.
3. On any error, `rollback()`: the previous save in that slot is untouched.

**Dry run: slot 3 already holds a save (version 1, gold 15). Saving again with gold 1014, crash simulation ON.**

| Step | Statement | Rows affected (inside the transaction) | What other connections see |
|---|---|---|---|
| 1 | `setAutoCommit(false)` | — | old save (v1, gold 15) |
| 2 | `INSERT … ON CONFLICT … DO UPDATE … RETURNING save_id` | slot row: v2, gold 1014 (triggers `touch_save` + `audit_save` fire) | old save (v1, gold 15) |
| 3 | 6 × `DELETE FROM save_* WHERE save_id = ?` | old child rows gone | old child rows |
| 4 | batch `INSERT INTO save_inventory …` | 2 new rows | old child rows |
| 5 | **simulated crash** → `SQLException` | — | — |
| 6 | `rollback()` | every change from steps 2–4 undone, audit row included | old save (v1, gold 15) — **intact** |

`SaveFailedException("Save rolled back; previous save intact")` reaches the API as HTTP 500 (`SaveRepositoryIT.crashedSaveRollsBackAndKeepsThePreviousSave`).
Without the crash, steps 7–11 insert flags, reputation, affinity, quests (`?::quest_status`) and history (step_no 1 = oldest). Then `commit()` makes everything visible at once, and the version becomes 2 (`overwritingBumpsVersionAndAudits`).

**Complexity: O(n) time for n rows, in a constant number of round trips**
- 1 upsert + 6 deletes + 6 batches. Each batch sends all of its rows in one round trip, instead of one round trip per row.
- Space O(1) beyond the batch buffers.

**Common mistakes**
- Leaving auto-commit on: each statement commits alone, and a crash leaves a half-written save.
- Catching the exception and **not** rolling back. The connection closes with the transaction open; PostgreSQL rolls it back, but only by luck.
- Inserting child rows without deleting the old ones first: duplicate-key errors.
- `executeUpdate` per row instead of batches: 50 rows means 50 network round trips.
- Writing history newest-first: the journal loads reversed (S7).
- Building SQL by string concatenation of values: SQL injection. Only constant table names are concatenated; all values are `?` parameters.

**Viva questions**
1. *Which ACID property does this demonstrate?* — Atomicity: all rows or none. Also Isolation: until commit, other connections still see the old save. And Durability after commit.
2. *Why `INSERT … ON CONFLICT … DO UPDATE` instead of DELETE + INSERT of the slot?* — It keeps the same `save_id` (child FKs and the audit trail stay consistent), increments `version`, and fires the UPDATE triggers.
3. *What do `touch_save` and `audit_save` do here, and are they rolled back?* — `touch_save` sets `updated_at`; `audit_save` writes a `save_audit` row. Both run inside the same transaction, so a rollback removes the audit row too.
4. *Why `?::quest_status`?* — The driver sends Java strings as `varchar`; PostgreSQL needs an explicit cast to the enum type (D5).
5. *What happens if the database is down?* — `getConnection()` throws before the transaction starts. It becomes `SaveFailedException("DB unavailable")`; no state changed.
