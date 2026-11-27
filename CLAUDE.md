# CLAUDE.md — Shardbound

## Vision
Shardbound is a 2D top-down, walkable dark-fantasy RPG on 32×32 pixel art: carry the Heartshard across six regions of Valdren.
Visible foes start server-authoritative, turn-based battles in a classic monster-battler layout, with elements, statuses, skills and two-phase bosses.
Souls-like progression: rest at Lanterns, spend Shards on six attributes and weapon upgrades, and risk them as Lost Shards on every death.
It is a public, mobile-first web game and a flagship portfolio piece: guest and Google accounts, anonymous telemetry, a balance simulator and a playtest bot.

**Specs:** `docs/V2_SPEC.md` is the source of truth. `docs/PROJECT_SPEC.md` is the historical v1 spec; its engine rules (conditions, ConditionGroup, effects, quest state machine, combat, endings) still apply where V2_SPEC does not override them. Read the sections for the current phase before writing code. If a spec and a request conflict, ask before acting.
Decision history: `docs/DECISIONS.md` (P-01 … P-69 win over older entries).

## Tech
- Backend: **Spring Boot 4.2** on **Java 25**; **JdbcClient** with hand-written SQL; **Flyway via Spring**; springdoc-openapi; JUnit 5, **Testcontainers**, ArchUnit.
- Database: **PostgreSQL 17**, player data only (JSONB saves, `active_run`). Content is JSON in `content/`, validated against `content/schema/` and loaded at startup.
- Frontend: **TypeScript**, React (HUD, menus, dialogue, auth), Phaser 4 (world and battle, lazy-loaded; own build, P-60), Vite, Vitest, Playwright. The API client is **generated from `api/openapi.json`** (openapi-typescript + openapi-fetch).
- Ops: **Docker Compose** (postgres + mailpit for dev), GitHub Actions, Dependabot, gitleaks, release-please, Cloudflare Turnstile, Sentry.

## Why the rules below exist
The project is judged as a product: players on phones, and reviewers reading a public repo. The rules keep it correct, secure, maintainable and pleasant to play.

## Rules
1. **The server owns the game rules.** The client renders server views, sends intents and moves the player tile by tile (P-51); the server validates every event (V2_SPEC §4.11). No damage math, encounter rolls, loot or quest logic in the frontend.
2. **Engine packages are framework-free.** No Spring, `jakarta.*` or `java.sql` in `engine`, `combat`, `quest`, `rules`, `dialogue`, `world`, `item`, `model`, `ds`, `event`, `achievement`, `content`, `exception`, `save`, `view`. ArchUnit enforces it; persistence goes through `engine/port/`.
3. **JdbcClient + hand-written SQL.** No JPA, Hibernate or other ORM. Never edit a merged migration; add a new one.
4. **Multi-row writes run in one transaction** (`@Transactional`), including every account change with its `email_outbox` row.
5. **Content and balance are data:** schema-validated JSON (and Tiled `.tmj`) in `content/`. Never hardcode content or balance in Java or TypeScript.
6. **Production-quality code.** Clear names, small units, no dead code. Complexity notes only on non-obvious algorithms.
7. **Tests cover behaviour.** A bug fix starts with a failing test. Never weaken or delete a test to make it pass.
8. **Security:** OWASP ASVS 5.0 Level 1. No secrets in git; configuration comes from environment variables (`.env.example` lists them); gitleaks runs in CI.
9. **Conventional Commits**, one branch and one PR per phase, green CI before merge, semver releases with a CHANGELOG.
10. **Libraries are chosen on merit.** Name every new runtime dependency with its reason and get my OK before adding it.
11. **Mobile-first UI:** touch controls, landscape responsive layout, touch targets ≥ 48 px, the V2_SPEC §4.13 performance budget.
13. **Phaser is our own build (P-60).** Any Phaser class, game object, factory or plugin used for the first time must be added to `frontend/src/world/phaserBuild.cjs` and covered by an e2e or scene test: a missing module fails only at runtime.
12. **Report every phase:** files changed and why, how to run it, what was verified, and known gaps.

## Environment
- Windows, PowerShell
- JDK 25, Maven wrapper (`mvnw.cmd`), Node 24, Docker Desktop (needed for `npm run dev` and `mvnw verify`)
- Branches: `main` is the trunk; v1 is tag `v1-archive`; each phase gets one branch and one PR into `main`

## Common commands
```powershell
# Everything for local dev (repo root): PostgreSQL + Mailpit via compose, API on :8080, Vite on :5173
npm install; npm run dev

# Backend
cd backend; .\mvnw.cmd test               # unit tests, ArchUnit, content schemas, OpenAPI export (no Docker)
cd backend; .\mvnw.cmd verify             # + Testcontainers integration tests (needs Docker)
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"   # API only; starts compose itself

# Frontend (types are generated from api/openapi.json first)
cd frontend; npm run typecheck; npm test; npm run build

# End-to-end (build the jar first: cd backend; .\mvnw.cmd -DskipTests package)
npm run e2e                               # Playwright; own backend on :18080; RESUME_CYCLES=20 for the nightly depth

# Dev services only
docker compose up -d
```

## Workflow
- **Phase gates:** finish one phase at a time (V2_SPEC §12). A phase is done when its acceptance criteria pass, all tests are green and the game is playable. Do not start the next phase until I say so.
- Start each phase in plan mode: list the files you will create or change, then wait for my OK.
- Don't add features outside the spec. Put ideas under a "Suggestions" heading instead.
- Commit after each working step with a Conventional Commit message (e.g. `feat(world): validate exit events with a speed check`).
- Run the tests before saying something is done.
- End each phase with the rule 12 report.

## Current phase
**V7 — Eldmere boss** (planning on `v7-eldmere-boss`; V6a merged via PR #25; no public alpha, launch as 1.0, P-66) (update this line as we progress)
