# CLAUDE.md — Shardbound

## Vision (v2)
Shardbound is a 2D top-down, walkable dark-fantasy RPG on 32×32 pixel art: carry the Heartshard across six regions of Valdren.
Visible foes start Java-authoritative, turn-based battles in a classic monster-battler layout, with elements, statuses, skills and two-phase bosses.
Souls-like progression: rest at Lanterns, spend Shards on six attributes and weapon upgrades, and risk them as Lost Shards on every death.
Real email accounts, anonymous telemetry, a balance simulator and a playtest bot make it a portfolio-grade, publicly deployable game.
It started as a college project for **Java-2, Data Structures, and DBMS**, and those graded parts stay visible: Java rules, hand-written SQL, chosen data structures.

**Specs:** `docs/V2_SPEC.md` is the source of truth for v2 work. `docs/PROJECT_SPEC.md` (v1) applies wherever V2_SPEC does not override it. Read the sections relevant to the current phase before writing code. If a spec and a request conflict, ask before acting.
Decision history: docs/DECISIONS.md

## Tech (v2 changes)
- Backend unchanged at its core: Java 21, Javalin 6 (now also **WebSocket** `/ws/world`), plain JDBC.
- **Flyway** runs hand-written SQL migrations (`backend/src/main/resources/db/migration/V*__*.sql`).
- **Bouncy Castle** provides Argon2id. Email goes through a transactional outbox (SMTP; Mailpit in dev).
- Frontend: **TypeScript**. React for the HUD, menus, dialogue and auth pages; **Phaser 3** for the world and battle scenes (lazy-loaded). A small typed event store connects them. Vitest + Playwright.
- Ops: **Docker Compose** (postgres, migrate, backend, nginx, mailpit), **GitHub Actions** CI, Cloudflare Turnstile.

## Why the rules below exist
The project is graded on visible Java, hand-written SQL, and deliberately chosen data structures. Anything that hides those (ORMs, logic in the frontend, hardcoded content) loses marks, even if it works.

## Non-negotiable rules
1. **All game rules live in Java.** React and Phaser only render the server's views and send player intents. No damage math, condition checks, encounter rolls, loot or quest logic in the frontend. The one exception: the client may *predict* grid movement for responsiveness, and the server validates and corrects every step.
2. **No Spring Boot, no JPA/Hibernate, no ORM.** Use Javalin + plain JDBC + `PreparedStatement` with hand-written SQL. Flyway (migrations runner) and Bouncy Castle (Argon2id) are allowed. New dependencies need my OK.
3. **Multi-row writes run in one transaction** (`setAutoCommit(false)` → commit, rollback on failure). This includes every account change together with its `email_outbox` row.
4. **Content is data.** Story, dialogue, maps (Tiled `.tmj`), creeps, bosses, skills, cutscenes and balance constants live in `content/**`, are imported into PostgreSQL, and are loaded into memory at startup. Never hardcode content in Java or TypeScript.
5. **Use the data structures listed in PROJECT_SPEC §5 and V2_SPEC §5.** Don't substitute one without asking.
6. Every non-trivial method gets a short comment stating time/space complexity.
7. **Phase gates:** finish one phase at a time (V2_SPEC §12: V1, V2a, V2b, V3 … V11a–V11d, V12). A phase is done only when its acceptance criteria pass, all tests are green and the game is playable. Do not start the next phase until I say so.
8. **Algorithm methods I must explain in my viva.** Claude implements the algorithm methods, but each must have: step-by-step comments, complexity in the Javadoc, and a section in docs/STUDY_GUIDE.md.
   - `ds/Graph` — `bfs`, `dfs`, `shortestPath`, `dijkstra`
   - `quest/QuestManager.topoOrder` (Kahn's algorithm)
   - `persistence/SaveRepository.save` (+ helpers: the save transaction)
   - `progression/Curve.valueAt` (V5: TreeMap `floorEntry`/`ceilingEntry` interpolation)
   - `auth/SlidingWindowRateLimiter` (V2a: Deque of timestamps)

   Standard for these: follow the Javadoc contract, make the existing tests pass without weakening any test, plain loops and clear names (rule 12, no streams inside these methods). Each STUDY_GUIDE section has: the idea in 3 lines, a dry run on a small example with a table of the queue/stack/map contents at each step, the complexity and why, common mistakes, and 5 viva questions with answers.
9. After each phase, give me:
   - which files changed and why
   - how to run it
   - 3 viva questions a strict professor might ask about that code, with model answers
10. Don't add features outside the spec. Put ideas under a "Suggestions" heading instead.
11. **Secrets come from environment variables** (`DB_URL`, `DB_APP_USER`, `DB_APP_PASSWORD`, `SMTP_*`, `TURNSTILE_SECRET`, …; full list in V2_SPEC §4.10). Never commit them. `backend/src/main/resources/db.properties` (gitignored) is a local-dev fallback only; only `db.properties.example` and `.env.example` are committed.
12. Keep code readable for a 3rd-semester student: clear names, small methods, no clever one-liners where a loop is clearer.

## Environment
- Windows, PowerShell
- Java 21 language level (JDK 25 installed), Maven wrapper (`mvnw.cmd`), Node 20+, PostgreSQL 16 running locally; Docker Desktop from V2a
- Branches: `main` holds v1 (tag `v1-semester`); all v2 work happens on `v2`

## Common commands
```powershell
# Database: v1 scripts until Flyway lands in V1, then migrations run automatically
psql -U postgres -c "CREATE DATABASE shardbound;"
psql -U postgres -d shardbound -f database/01_schema.sql
psql -U postgres -d shardbound -f database/02_functions_triggers.sql
psql -U postgres -d shardbound -f database/03_views_indexes.sql

# Backend (exec:java does not compile by itself: keep `compile` in front)
cd backend; .\mvnw.cmd test
cd backend; .\mvnw.cmd verify
cd backend; .\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.ContentImporter"
cd backend; .\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.ConsoleRunner"
cd backend; .\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.App"

# Frontend
cd frontend; npm install; npm run dev
cd frontend; npm test; npm run build

# Full stack (from V2a)
docker compose -f infra/docker-compose.yml up --build
```

## Workflow
- Start each phase in plan mode: list the files you'll create or change, then wait for my OK.
- Commit after each working step with a clear message (e.g. `feat(world): validate grid steps over WebSocket`).
- Run tests before saying something is done.

## Current phase
**v2 design** (update this line as we progress)
