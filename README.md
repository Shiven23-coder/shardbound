# Shardbound

A 2D top-down dark-fantasy RPG for phone and desktop browsers. Carry the Heartshard across six regions of Valdren, fight turn-based battles against foes you can see coming, rest at Lanterns, and spend Shards on attributes and weapon upgrades, knowing you drop them all when you die. The server decides every outcome; the browser renders it.

Spring Boot 4.1 · Java 25 · PostgreSQL 17 · React + TypeScript (Phaser for the world and battles)

## Prerequisites
- JDK 25 (`JAVA_HOME` set). Maven is downloaded by the wrapper.
- Node 24 and npm.
- Docker Desktop, running. It provides PostgreSQL and Mailpit for development and the Testcontainers database for integration tests.

## Quick start
From the repo root:
```powershell
npm install
npm run dev
```
This starts PostgreSQL 17 and Mailpit with Docker Compose, the API on http://localhost:8080 (Spring `dev` profile; Flyway migrates the schema on startup) and Vite on http://localhost:5173, which proxies `/api` to the API. Open http://localhost:5173. Mailpit's inbox is at http://localhost:8025.

Only need the database and mail server? Run `docker compose up -d`.

## Backend
```powershell
cd backend
.\mvnw.cmd test               # unit tests, ArchUnit, content schemas, OpenAPI export (no Docker needed)
.\mvnw.cmd verify             # + Testcontainers integration tests (needs Docker)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"    # API only, on :8080; starts the compose services
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.main-class=com.shardbound.tools.GraphExporter"   # story graph as DOT
```
`test` also exports the OpenAPI contract to `api/openapi.json`. Commit it when it changes; CI fails on drift.

## Frontend
```powershell
cd frontend
npm run gen:api      # regenerate TypeScript types from api/openapi.json (runs automatically before dev, build, typecheck and test)
npm run typecheck
npm test             # Vitest
npm run build
```

## Configuration
Settings come from environment variables bound to Spring properties. [.env.example](.env.example) lists them. The dev profile needs none: the compose file's localhost-only credentials are wired in automatically and are not secret. Never commit real secrets.

## Tools
```powershell
node tools/balance/progression.mjs     # progression model (V2_SPEC §10.2)
```

## CI
GitHub Actions ([.github/workflows/ci.yml](.github/workflows/ci.yml)) on every push and PR:
- backend `mvnw verify` on Java 25 with Testcontainers, then the OpenAPI drift check;
- frontend typecheck, Vitest and build on Node 24;
- gitleaks secret scanning and a Conventional Commits PR-title check.

Dependabot keeps dependencies current. release-please tags semver releases and writes `CHANGELOG.md`.

## Branches
`main` is the trunk. Each phase is one branch and one squash-merged PR. v1 is preserved at tag `v1-archive`.

## Docs
- [docs/V2_SPEC.md](docs/V2_SPEC.md): the specification (source of truth)
- [docs/DECISIONS.md](docs/DECISIONS.md): why each decision was made
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/TECH_STACK.md](docs/TECH_STACK.md) · [docs/PRD.md](docs/PRD.md) · [docs/ALGORITHMS.md](docs/ALGORITHMS.md)
- Diagrams: [ER](docs/diagrams/er-diagram-v2.md) · [classes](docs/diagrams/class-diagram-v2.md)
- [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md): the historical v1 spec; its engine rules still apply
