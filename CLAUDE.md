# CLAUDE.md — Shardbound

Shardbound is a turn-based, choice-driven story RPG built as a college project for **Java-2, Data Structures, and DBMS**.
The full design lives in `docs/PROJECT_SPEC.md` and is the source of truth. Read the sections relevant to the current phase before writing code. If the spec and a request conflict, ask before acting.
Decision history: docs/DECISIONS.md

## Why the rules below exist
The project is graded on visible Java, hand-written SQL, and deliberately chosen data structures. Anything that hides those (ORMs, logic in React, hardcoded story) loses marks, even if it works.

## Non-negotiable rules
1. **All game rules live in Java.** React only renders the `GameView` JSON and POSTs player intents. No damage math, condition checks, or quest logic in the frontend.
2. **No Spring Boot, no JPA/Hibernate, no ORM.** Use Javalin + plain JDBC + `PreparedStatement` with hand-written SQL.
3. **Multi-row writes run in one transaction** (`setAutoCommit(false)` → commit, rollback on failure).
4. **Story content is data**, authored in `content/*.json`, imported into PostgreSQL by `ContentImporter`, loaded into memory at startup. Never hardcode story nodes in Java.
5. **Use the data structures listed in spec section 5.** Don't substitute one without asking.
6. Every non-trivial method gets a short comment stating time/space complexity.
7. **Phase gates:** finish one phase at a time (spec section 10). A phase is done only when its acceptance criteria pass and all tests are green. Do not start the next phase until I say so.
8. **Student-implemented parts** (I must explain these in my viva):
   - `ds/Graph` — `bfs`, `dfs`, `shortestPath`
   - `quest/QuestManager.topoOrder` (Kahn's algorithm)
   - `persistence/SaveRepository.save` (the save transaction)

   For these: create the class, method signatures, Javadoc, and failing JUnit tests, then **stop and let me implement**. Afterwards, review my code for bugs and complexity.
   *(Delete rule 8 if you want Claude to write these too.)*
9. After each phase, give me:
   - which files changed and why
   - how to run it
   - 3 viva questions a strict professor might ask about that code, with model answers
10. Don't add features outside the spec. Put ideas under a "Suggestions" heading instead.
11. Secrets: `backend/src/main/resources/db.properties` is gitignored; only `db.properties.example` is committed.
12. Keep code readable for a 3rd-semester student: clear names, small methods, no clever one-liners where a loop is clearer.

## Environment
- Windows, PowerShell
- Java 21, Maven wrapper (`mvnw.cmd`), Node 20+, PostgreSQL 16 running locally

## Common commands
```powershell
# Database (from repo root)
psql -U postgres -c "CREATE DATABASE shardbound;"
psql -U postgres -d shardbound -f database/01_schema.sql
psql -U postgres -d shardbound -f database/02_functions_triggers.sql
psql -U postgres -d shardbound -f database/03_views_indexes.sql

# Backend
cd backend; .\mvnw.cmd test
cd backend; .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.tools.ContentImporter
cd backend; .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.tools.ConsoleRunner
cd backend; .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.App

# Frontend
cd frontend; npm install; npm run dev
```

## Workflow
- Start each phase in plan mode: list the files you'll create or change, then wait for my OK.
- Commit after each working step with a clear message (e.g. `feat(story): add StoryValidator BFS checks`).
- Run tests before saying something is done.

## Current phase
**Phase 0 — Design** (update this line as we progress)
