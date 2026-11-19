# Shardbound — Tech Stack

What each technology is for and why it was chosen. Exact versions are pinned in `backend/pom.xml` (Spring Boot parent and BOMs), `frontend/package.json`, the root `package.json` and `docker-compose.yml`; Dependabot keeps them current. Reasons for the big choices: `DECISIONS.md` P-02 … P-24.

---

## 1. Summary

| Layer | Choice |
|---|---|
| Language | Java 25 (`maven.compiler.release=25`) |
| Framework | Spring Boot 4.1: web, JDBC, Flyway, Actuator, validation; Security (V2a); mail (V2b) |
| Data access | `JdbcClient` with hand-written SQL; `@Transactional` for multi-row writes |
| Migrations | Flyway (`backend/src/main/resources/db/migration/V*__*.sql`), run by Spring at startup |
| Database | PostgreSQL 17 |
| API contract | springdoc-openapi → `api/openapi.json` (committed) |
| Content validation | networknt json-schema-validator (JSON Schema 2020-12) |
| Live-run cache | Caffeine |
| Backend tests | JUnit 5, Spring Boot Test, Testcontainers (`postgres:17-alpine`), ArchUnit |
| Frontend | TypeScript, React 19, Phaser 3 (lazy-loaded), Vite |
| API client | openapi-typescript (generated types) + openapi-fetch |
| Frontend tests | Vitest, Playwright (e2e, budgets) |
| Dev services | Docker Compose: `postgres:17`, `mailpit` |
| Dev runner | `concurrently` (root `npm run dev`) |
| CI / release | GitHub Actions, gitleaks, PR-title lint, Dependabot, release-please |
| Bot protection | Cloudflare Turnstile (V2a) |
| Observability | Sentry, structured JSON logs, Actuator (V2c) |

---

## 2. Backend

### Spring Boot on Java 25
- **Why:** the industry default for Java services, with maintained security, observability and testing support. Java 25 is the current LTS.
- **Boundaries:** controllers, configuration and repositories use Spring. The engine packages do not (no Spring, `jakarta.*` or `java.sql`), and ArchUnit enforces it, so game rules stay plain Java that is fast to unit-test.

### JdbcClient + hand-written SQL
- **Why:** every query is visible and reviewable SQL, without connection, statement and mapping boilerplate.
- **Rejected:** JPA/Hibernate and other ORMs. The data model is small, saves are JSONB documents, and generated SQL would be harder to review and tune.

### Flyway
- **Why:** versioned, repeatable schema changes in plain SQL. Merged migrations are never edited.

### springdoc-openapi
- **Why:** generates the OpenAPI contract from the controllers, so the frontend types and the backend can never disagree silently. CI fails if `api/openapi.json` drifts.

### networknt json-schema-validator
- **Why:** the most complete and fastest JSON Schema 2020-12 validator on the JVM. Content errors point to the exact file and location and stop startup.

### Caffeine
- **Why:** a bounded, expiring cache for live `GameState`s. It evicts idle runs without a hand-written map and cleanup thread. `active_run` stays the source of truth.

### Testcontainers + ArchUnit
- **Why:** every integration test runs against a real PostgreSQL 17, the same locally and in CI. ArchUnit keeps the engine framework-free.

---

## 3. Database — PostgreSQL 17
- **Why:** robust standard SQL, JSONB for save documents, `FOR UPDATE SKIP LOCKED` for the email outbox, BRIN indexes and window functions for telemetry. 17 is the current managed-provider default.
- **What it stores:** player data only: players/accounts, save slots (JSONB + summary columns), active runs, achievements, endings, and later sessions, tokens, outbox, feedback and telemetry. Content is not in the database.

---

## 4. Frontend

### TypeScript, React and Phaser
- **Why:** React renders the HUD, menus, dialogue and auth pages; Phaser renders the world and battles and is loaded only after the title screen. A small typed event store connects them.
- **Rule:** the frontend renders server views, sends intents and moves the player; it never decides outcomes.

### Generated API client
- **Why:** openapi-typescript turns `api/openapi.json` into types (`src/api/schema.gen.ts`, gitignored, regenerated before dev, build, typecheck and test); openapi-fetch is a small typed fetch client (about 6 KB). There are no hand-written view types.

### Vitest and Playwright
- **Why:** Vitest covers the event store, mappers and client error handling. Playwright runs e2e on desktop and mobile viewports and measures the load and mobile performance budgets (from V3).

---

## 5. Ops and repository hygiene
- **Docker Compose** for dev services only; the API and Vite run on the host for hot reload.
- **GitHub Actions:** backend `mvnw verify` with Testcontainers and the OpenAPI drift check; frontend typecheck, test and build; gitleaks; Conventional Commits PR titles.
- **Dependabot:** maven, npm, github-actions and docker, weekly and grouped.
- **release-please:** semver tags and `CHANGELOG.md`, with backend and frontend versions in step.

---

## 6. Development environment

| Tool | Version |
|---|---|
| JDK | 25 (`JAVA_HOME` set) |
| Maven | via the wrapper (`mvnw.cmd`) |
| Node / npm | 24 |
| Docker Desktop | current; needed for `npm run dev` and `mvnw verify` |
| git | any recent |

### Ports
| Service | Port |
|---|---|
| Spring Boot API | 8080 |
| Vite dev server | 5173 (proxies `/api` → 8080) |
| PostgreSQL (compose) | 127.0.0.1:5432 |
| Mailpit SMTP / UI | 127.0.0.1:1025 / 127.0.0.1:8025 |
