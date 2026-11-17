# Shardbound — Syllabus Mapping

Where each graded concept lives in the code, SQL and tests.
**Skeleton:** each table is filled in at the end of the phase that introduces the code. A row is filled only when the file exists and its tests are green.

Last updated: Phase 0 (structure only).

---

## 1. Java-2

| Concept | Class / file | Method or line to show | Test | Phase |
|---|---|---|---|---|
| Inheritance / abstract classes | | | | 4, 5 |
| Interfaces / polymorphism | | | | 2, 5, 8 |
| Sealed interfaces, records, switch expressions | | | | 2, 3 |
| Generics | | | | 2, 4 |
| Enums with fields | | | | 2, 4, 6, 8 |
| Custom exceptions | | | | 2, 3 |
| Collections + streams | | | | all |
| Multithreading | | | | 3, 7 |
| JDBC (PreparedStatement, batch, transactions) | | | | 1–2, 7 |
| File I/O | | | | 2, 10 |
| Design patterns (Observer, Strategy, Factory, Repository, State machine) | | | | 2–8 |

## 2. Data Structures

| Structure / algorithm | Class / file | Why this structure | Complexity | Test | Phase |
|---|---|---|---|---|---|
| `HashMap` node lookup | | | | | 2 |
| Adjacency list graph `Graph<V,E>` | | | | | 2 |
| BFS (reachability, shortest path) | | | | | 2 |
| Iterative DFS (dead ends) | | | | | 2 |
| `LinkedHashMap` inventory | | | | | 4 |
| `enum Tier` + `Comparator` | | | | | 4 |
| `EnumMap` reputation / achievement rules | | | | | 6, 8 |
| `TreeMap` condition groups | | | | | 2 |
| Kahn's topological sort | | | | | 6 |
| `PriorityQueue` turn order | | | | | 5 |
| Bounded `ArrayDeque` combat log | | | | | 5 |
| `ArrayDeque` stack (history) / queue (toasts) | | | | | 3, 7 |
| `HashSet` unlocked achievements | | | | | 8 |
| `ConcurrentHashMap` sessions | | | | | 3 |

## 3. DBMS

| Concept | SQL file / object | What to show | Phase |
|---|---|---|---|
| Normalization (1NF–3NF), derived data removed | | | 1 |
| ISA / generalization (item → weapon, consumable) | | | 1 |
| Primary, foreign, unique keys; composite keys | | | 1 |
| CHECK constraints | | | 1 |
| ENUM types and ordering | | | 1 |
| Deferrable constraints | | | 1 |
| Referential actions (CASCADE / RESTRICT / SET NULL) | | | 1 |
| Transactions and ACID (save, import) | | | 2, 7 |
| Upsert (`ON CONFLICT`) | | | 2, 7 |
| Triggers (PL/pgSQL) | | | 1 |
| Functions | | | 1 |
| Views | | | 1 |
| Indexes (FK indexes, partial unique index) | | | 1 |
| SQL injection prevention | | | 2+ |

## 4. Spec traceability

| Spec section | Files | Tests | Phase | Status |
|---|---|---|---|---|
| §4 Database | | | 1 | ☐ |
| §5 Data structures | | | 2–8 | ☐ |
| §6.2 Conditions / effects | | | 2 | ☐ |
| §6.3 Choose flow | | | 2–3 | ☐ |
| §6.4 Inventory | | | 4 | ☐ |
| §6.5 Quests | | | 6 | ☐ |
| §6.6 Combat | | | 5 | ☐ |
| §6.8 Achievements | | | 8 | ☐ |
| §6.9 Endings | | | 8 | ☐ |
| §6.10 Save / load | | | 7 | ☐ |
| §8.3 Importer | | | 2 | ☐ |
| §8.4 Validator | | | 2 | ☐ |
| §9 API / frontend | | | 3+ | ☐ |
