# Shardbound — Product Requirements Document

| | |
|---|---|
| Product | Shardbound, a 2D top-down dark-fantasy RPG with turn-based battles, played in phone and desktop browsers |
| Purpose | A public game and a flagship portfolio piece |
| Source of truth | `docs/V2_SPEC.md` (this PRD explains the *why* and *for whom*; the spec defines the *what*) |
| Status | Phase P0 (platform reset), 2026-11-18 |

---

## 1. Problem and purpose

Shardbound should be a game people finish and recommend, and a codebase a reviewer can read with confidence. It has to be:
- **fun on a phone**: smooth movement, touch controls, short sessions that never lose progress;
- **fair and cheat-resistant**: the server decides every outcome;
- **well engineered**: a clear contract between client and server, real tests, safe handling of accounts and data, and a repeatable release process.

## 2. Users

| User | What they need |
|---|---|
| **Player** (mostly on mid-range Android phones, also desktop) | An atmospheric 1–2 hour story with readable, tactical battles; touch-friendly controls; fast loads; progress that survives closed tabs, restarts and deploys; sign-in without friction (guest or Google). |
| **Reviewer** (recruiters, engineers reading the public repo) | A clean architecture with a framework-free engine, hand-written SQL, a generated API contract, meaningful tests and CI, and documented decisions. |
| **Developer** | One-command local setup, fast tests, clear specs and phase gates. |

## 3. Goals and non-goals

### Goals
1. One launch, complete, as 1.0: all six regions on phone and desktop, after private playtests on staging (P-66).
2. All game rules on the server; the client renders, moves and sends intents.
3. No lost progress: durable active runs, safe saves with versioned upgrades.
4. Mobile-first: touch controls, responsive HUD, installable PWA, a measured performance budget.
5. Production hygiene: OWASP ASVS L1, privacy policy, data export and deletion, error tracking, backups with a tested restore.

### Non-goals
- Real-time multiplayer.
- Native mobile apps (the PWA covers installation).
- Paid content or ads.
- User-generated content.

## 4. Feature requirements

Phases and acceptance criteria: V2_SPEC §12. Summary:

| Area | Requirement | Phase |
|---|---|---|
| Platform | Spring Boot, JSON content, JSONB saves, durable runs, generated client, one-command dev | P0 |
| World | Free movement with server-validated events, Eldmere walkable, touch controls | V3 |
| Battles | Elements, statuses, skills, visible creeps | V4 |
| Progression | Shards, Lanterns, attributes, Lost Shards | V5 |
| Bosses | Ashen Herald with phases and telegraphs | V7 |
| Accounts + privacy | Guest + Google sign-in, export, deletion, policy and terms | V2a |
| Ops | Sentry, deploy, PWA, version skew handling | V2c |
| Private playtests | Eldmere (PLAYTEST 1), through the Hollow Deep (PLAYTEST 2), the complete game (PLAYTEST 3), with the feedback button | PLAYTEST 1–3 |
| Launch | The complete game live as 1.0, launch checklist | LAUNCH |
| Depth | Gear, navigation, email accounts, save slots, telemetry and tools | V6, V8, V2b, V9, V10 |
| Content | Remaining regions, six endings | V11a–d |
| Polish | Audio, accessibility, 1.0 | V12 |

## 5. Content requirements
- World: the Kingdom of Valdren; six regions, 28 areas, 85 creep fights, 8 bosses (V2_SPEC §10).
- Critical path 1–2 hours; side content +2–3 hours.
- Six endings, including the early secret merchant ending.
- Tone: hopeful dark fantasy, PG-13. Original names, art direction and UI; CC0/CC-BY assets credited in CREDITS.md.

## 6. Non-functional requirements

| Area | Requirement |
|---|---|
| Correctness | The server validates every action and world event; multi-row writes are atomic. |
| Durability | Every state-changing action is written through to `active_run`; restarts lose nothing. |
| Determinism | Seeded RNG, reseeded on each write; same seed + same actions = same combat. |
| Performance | Title interactive < 2 s; battle start < 300 ms; mid-range Android p95 frame ≤ 33 ms, JS heap ≤ 100 MB and textures ≤ 64 MB per region (V2_SPEC §4.13). |
| Security | OWASP ASVS 5.0 L1; Spring Security; CSRF protection; Turnstile; no secrets in git. |
| Privacy | Minimal data; anonymous telemetry with opt-out; export and deletion; Sentry without PII. |
| Compatibility | Current mobile and desktop browsers; old cached clients are told to reload (version skew handling). |
| Maintainability | Framework-free engine (ArchUnit), generated API types, Conventional Commits, semver releases. |

## 7. Success metrics
1. Playtesters reach the Ashen Herald on a phone without errors (Sentry clean, feedback reviewed).
2. Zero lost-progress reports across deploys.
3. The mobile performance budget holds in CI and on a real device for every region.
4. CI green on every merge: unit tests, ArchUnit, Testcontainers ITs, OpenAPI drift, frontend checks.
5. The full critical path plays within 120 minutes (playtest bot, V11d).

## 8. Risks
See V2_SPEC §13.
