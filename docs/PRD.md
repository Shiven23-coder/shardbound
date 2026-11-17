# Shardbound — Product Requirements Document

| | |
|---|---|
| Product | Shardbound, a turn-based, choice-driven story RPG played in the browser |
| Context | College project for Java-2, Data Structures, and DBMS |
| Source of truth | `docs/PROJECT_SPEC.md` (this PRD explains the *why* and *for whom*; the spec defines the *what*) |
| Status | Approved for Phase 1, 2026-11-18 |

---

## 1. Problem and purpose

The project must prove three courses' worth of skills in one working system:

- **Java-2:** OOP, generics, collections, exceptions, JDBC, multithreading, file I/O.
- **Data Structures:** graphs, BFS/DFS, topological sort, priority queues, hashing — chosen deliberately and with known complexity.
- **DBMS:** a normalized schema, constraints, transactions, triggers, functions, views and indexes, all in hand-written SQL.

A story RPG fits this well. A branching story *is* a graph, quest dependencies *are* a DAG, combat turn order *is* a priority queue, and save files *need* transactions. Every graded concept has a natural reason to exist instead of being bolted on.

## 2. Users

| User | What they need |
|---|---|
| **Player** (classmates, demo audience) | A short, atmospheric story where choices visibly matter; clear feedback (toasts, locked-choice reasons); a fair, readable combat system; saves that never corrupt. |
| **Examiner** (professor, viva panel) | Visible Java, visible SQL, visible data structures. A way to see inside the running game (Inspector panel, SQL log). Demonstrations of correctness: rollback on a crashed save, deterministic seeded combat, a validator report. |
| **Developer / student** | Code readable at a 3rd-semester level, small methods, complexity comments, and parts they implement themselves and can explain (`Graph`, `topoOrder`, `SaveRepository.save`). |

## 3. Goals and non-goals

### Goals
1. A complete quick-ending route (p01 → END_QUICK) that takes 6–8 minutes and shows every MVP system. It is the demo backbone.
2. All game rules in Java; React is a pure renderer.
3. Story content authored as JSON data, imported into PostgreSQL, loaded into memory at startup.
4. Every graded concept is visible in a named class, SQL object or test (see `SYLLABUS_MAPPING.md`).
5. The demo cannot fail: seeded RNG, Demo Mode, prepared save slots, a validator that refuses broken content.

### Non-goals
- Multiplayer, real authentication or passwords (a username is the profile).
- Deployment to the internet; it runs on localhost.
- Any ORM, Spring, or framework that hides SQL or wiring.
- Random loot drops (weapons are deterministic quest rewards).
- Mobile-first design (desktop browser is the target; it should not break on smaller screens).

## 4. Feature requirements

Priorities follow spec §1: **MVP** must ship; **Core+** after MVP works; **Stretch** only if time remains.

### 4.1 MVP

| ID | Feature | User story | Acceptance |
|---|---|---|---|
| M1 | Story graph | As a player, I read a scene and pick from 2–4 choices so the story moves on. | Nodes and choices load from DB; `GameView` shows text and choices. |
| M2 | Conditions and effects | As a player, I see locked choices with a reason, so I know what earlier decisions matter. | Conditions (FLAG, REP, AFFINITY, ITEM, QUEST, STAT) with AND/OR groups; locked choices show `lockReason`; server rejects locked choices with 403. |
| M3 | Inventory and weapon tiers | As a player, I collect and equip weapons from F to SSS and feel each upgrade. | Capacity 20; one equipped weapon; tier multiplier affects damage; tier styling in UI. |
| M4 | Combat | As a player, I fight turn-based battles with attack, defend, skill, item and flee. | Speed-ordered turns; damage formula unit-tested with a fixed seed; win/lose/flee lead to the right nodes. |
| M5 | Quests | As a player, I see a quest log in a sensible order and get rewards on completion. | State machine LOCKED→AVAILABLE→ACTIVE→COMPLETED/FAILED; topological order; cycle detection at startup. |
| M6 | Save / load | As a player, I save to one of 3 slots and load later, even after a server restart. | Save in one transaction; autosave on chapter change; crash simulation proves rollback. |
| M7 | Endings | As a player, my accumulated choices decide which of 6 endings I reach. | `EndingResolver` picks by priority; ending recorded per player with best time. |
| M8 | Quick ending | As a demo presenter, I can show the whole game in under 8 minutes. | p01 → END_QUICK playable, ≤ 12 steps by shortest path. |

### 4.2 Core+

| ID | Feature | Acceptance |
|---|---|---|
| C1 | Achievements | 9 achievements; gameplay ones fire from Java events, `ALL_ENDINGS` from a DB trigger; unlocks persist per profile. |
| C2 | Reputation and affinity | 4 factions, per-NPC affinity, clamped −100…100, shown in a panel. |
| C3 | Leaderboard | Backed by the `v_leaderboard` view. |
| C4 | Story validator | 10 checks (spec §8.4); import refuses content with errors. |
| C5 | Inspector panel | Toggle with `~`: node id, flags, reputation, quests in topo order, last 5 SQL statements. |
| C6 | Demo Mode | Enemy hp/atk ×0.6, toggled on the title screen. |

### 4.3 Stretch
Audio, shop (item prices already exist in the schema), NPC portraits, extra chapters.

## 5. Content requirements

- World: the Kingdom of Valdren; 4 factions (ELDMERE, SYLVAN, CROWN, ASHEN); regions VILLAGE, FOREST, DUNGEON, KINGDOM, SPIRE.
- Chapters 0–5, 70–90 nodes total, branch-and-bottleneck structure.
- Node text: 80–160 words, second person, present tense; tone hopeful dark fantasy, PG-13.
- Every reputation change has a visible narrative reason.
- 6 endings exactly; the weapon ladder exactly as spec §6.4.

## 6. Non-functional requirements

| Area | Requirement |
|---|---|
| Correctness | Server re-validates every choice; React is never trusted. Multi-row writes are atomic. |
| Determinism | Seeded `Random`, reseeded at save time; same seed + same actions = same combat. |
| Performance | Content in memory: node lookup O(1), choice listing O(k). Each request does at most one small DB transaction. Target < 50 ms per request on localhost. |
| Concurrency | Sessions in `ConcurrentHashMap`; each request on a state runs under that state's lock; autosave on a single background thread using a deep copy. |
| Security | `PreparedStatement` everywhere (no SQL injection); secrets only in gitignored `db.properties`. |
| Robustness | Errors map to JSON `{error, message}` with 400/403/409/500. Content mistakes are caught at import, never as crashes during play. |
| Readability | 3rd-semester level: clear names, small methods, a complexity comment on every non-trivial method; React components under ~120 lines. |
| Testability | Unit tests per phase (JUnit 5); `SaveRepositoryIT` against a separate test database. |
| Portability | Windows + PowerShell; Java 21 language level; PostgreSQL 16; Node 20+. |

## 7. Success metrics (for the demo and viva)

1. The quick route plays start to finish with no errors, in 6–8 minutes.
2. The validator reports all 6 endings reachable and zero errors (Phase 9).
3. A crashed save visibly rolls back and the previous save still loads.
4. All tests green: `mvnw test` and `mvnw verify`.
5. The student can explain BFS/DFS, Kahn's algorithm and the save transaction line by line.

## 8. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Content grows faster than it can be checked | `StoryValidator` blocks bad imports; branch-and-bottleneck keeps node count bounded. |
| Demo combat goes badly | Seeded RNG, Demo Mode, prepared save slots before boss fights. |
| A re-import breaks existing saves | Upsert + RESTRICT + clear abort message (D1). |
| Scope creep | Phase gates; ideas go under "Suggestions", never straight into code. |
| Logic leaks into React | Code review rule: React only renders `GameView` and posts intents. |

## 9. Milestones

The 11 phases in spec §10, each gated by its acceptance criteria and green tests. The quick route (MVP vertical slice) is complete at the end of Phase 8; full content at Phase 9; polish at Phase 10.
