# Shardbound

A turn-based, choice-driven story RPG — college project for Java-2, Data Structures and DBMS.
Java 21 (Javalin + plain JDBC) · PostgreSQL 16 · React + Vite.

- Design and rules: [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md) (source of truth) · why: [docs/DECISIONS.md](docs/DECISIONS.md)
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · algorithms: [docs/ALGORITHMS.md](docs/ALGORITHMS.md) · syllabus map: [docs/SYLLABUS_MAPPING.md](docs/SYLLABUS_MAPPING.md)

## Prerequisites
JDK 21+ (`JAVA_HOME` set), Node 20+, PostgreSQL 16 (`psql` on PATH). Maven is downloaded by the wrapper.

## 1. Database (from the repo root)
```powershell
psql -U postgres -c "CREATE DATABASE shardbound;"
psql -U postgres -d shardbound -f database/01_schema.sql
psql -U postgres -d shardbound -f database/02_functions_triggers.sql
psql -U postgres -d shardbound -f database/03_views_indexes.sql
```
For the integration test also create `shardbound_test` (the test rebuilds its schema itself):
```powershell
psql -U postgres -c "CREATE DATABASE shardbound_test;"
```
Start over at any time with `database/99_reset.sql`, then run 01–03 again.

## 2. Configuration
```powershell
copy backend\src\main\resources\db.properties.example backend\src\main\resources\db.properties
```
Fill in your PostgreSQL password. `db.properties` is gitignored.

## 3. Backend
```powershell
cd backend
.\mvnw.cmd test                                   # unit tests (no database needed)
.\mvnw.cmd verify                                 # + SaveRepositoryIT on shardbound_test
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.ContentImporter" "-Dexec.args=--dry-run"   # validate content only
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.ContentImporter"                           # import content/*.json
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.ConsoleRunner"                             # play in the terminal (DB)
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.ConsoleRunner" "-Dexec.args=--json"        # play offline (no DB)
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.ConsoleRunner" "-Dexec.args=--json --auto-quick"   # quick-route check
.\mvnw.cmd compile exec:java "-Dexec.mainClass=com.shardbound.tools.GraphExporter"                             # docs/story-graph.dot
.\mvnw.cmd compile exec:java                                                                                    # API on :7070
```
`exec:java` does not compile by itself, so keep `compile` in front.

## 4. Frontend
```powershell
cd frontend
npm install
npm run dev          # http://localhost:5173, proxies /api to :7070
```

## Student-implemented parts
`ds/Graph.bfs`, `dfs`, `shortestPath` · `quest/QuestManager.topoOrder` · `persistence/SaveRepository.save`.
They throw `UnsupportedOperationException("TODO(student)…")` until written; `GraphTest`, `QuestManagerTest`, `StoryValidatorTest`, `GameEngineTest` and `SaveRepositoryIT` go green once they are done.
