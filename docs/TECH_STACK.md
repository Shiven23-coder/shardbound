# Shardbound — Tech Stack

What each technology is, which version is pinned, why it was chosen, and what was deliberately rejected.
Constraints come from `CLAUDE.md`: visible Java, hand-written SQL, no ORM, no Spring.

---

## 1. Summary

| Layer | Choice | Version | Pinned in |
|---|---|---|---|
| Language | Java (language level) | 21 | `backend/pom.xml` → `maven.compiler.release` |
| JDK on the dev machine | Oracle JDK | 25.0.2 (any JDK ≥ 21 works) | — |
| Build | Apache Maven via wrapper | 3.9.11 (wrapper 3.3.2) | `backend/.mvn/wrapper/maven-wrapper.properties` |
| Web server | Javalin | 6.7.0 | `pom.xml` |
| Database driver | PostgreSQL JDBC | 42.7.7 | `pom.xml` |
| JSON | Jackson Databind | 2.19.2 | `pom.xml` |
| Logging | SLF4J Simple | 2.0.17 | `pom.xml` |
| Tests | JUnit Jupiter (5) | 5.13.4 | `pom.xml` (BOM) |
| Test runners | Surefire / Failsafe | 3.5.3 | `pom.xml` |
| Database | PostgreSQL | 16 | installed locally (with pgAdmin) |
| Frontend framework | React | 19.x | `frontend/package.json` |
| Frontend build | Vite | 7.x | `frontend/package.json` |
| Animation | Framer Motion | 12.x | `frontend/package.json` |
| Styling | Plain CSS with custom properties | — | `frontend/src/styles/` |
| Runtime for the frontend tooling | Node.js | ≥ 20 (dev machine: 24) | — |

---

## 2. Backend

### Java 21 (language level)
- **Why:** records (DTOs), sealed interfaces (`Condition`), switch expressions and pattern matching are all syllabus items and make the rules code short and safe. Java 21 is the current LTS taught in the course.
- **How:** `maven.compiler.release=21` compiles against the Java 21 API even on a newer JDK, so nothing from Java 22+ can slip in (S9).

### Maven + wrapper
- **Why:** standard Java build; the wrapper (`mvnw.cmd`) downloads the exact Maven version on first run, so the examiner's machine needs only a JDK.
- **Rejected:** Gradle — fine, but its build script is code and adds a second language to explain.

### Javalin 6
- **Why:** a thin library over Jetty. Routes are plain lambdas (`app.post("/api/game/choose", ctx -> …)`), so every request path is visible Java. Jetty's thread pool gives real multithreading to discuss.
- **Rejected:** **Spring Boot** (forbidden by CLAUDE.md rule 2): annotations and auto-configuration hide wiring, dependency injection and transactions — exactly what must be visible for marks. Plain `HttpServer` from the JDK: works, but JSON, routing and error mapping would be boilerplate.

### Plain JDBC + PostgreSQL driver
- **Why:** every query is a hand-written `PreparedStatement`. Transactions are explicit (`setAutoCommit(false)` / `commit` / `rollback`). Batch inserts use `addBatch` / `executeBatch`.
- **Rejected:** **JPA / Hibernate / any ORM** (forbidden): generates SQL you cannot show. jOOQ / MyBatis: still a layer between the student and the SQL.
- **HikariCP:** optional per spec. Not added in Phase 0; decide in Phase 3 if connection setup becomes a bottleneck. Without it, `Database` opens a connection per unit of work.

### Jackson
- **Why:** reads `content/*.json` into Java objects (file I/O) and serialises `GameView` records for Javalin. Javalin detects Jackson automatically.

### SLF4J Simple
- **Why:** Javalin logs through SLF4J and needs a backend. Also used for D14 runtime warnings (invalid quest transitions). Simple prints to the console — enough for a local app.

### JUnit 5, Surefire, Failsafe
- **Why:** `*Test` classes run with `mvnw test` (fast, no DB). `*IT` classes (`SaveRepositoryIT`) run with `mvnw verify` against `shardbound_test` (S6).

### exec-maven-plugin
- **Why:** runs `App`, `ContentImporter` and `ConsoleRunner` with `mvnw exec:java -Dexec.mainClass=…`.

---

## 3. Database — PostgreSQL 16

- **Why:** strong standard SQL plus features that are on the DBMS syllabus and simple to show: `ENUM` types with ordering (weapon tiers), `CHECK` constraints, deferrable foreign keys (forward references between story nodes), `ON CONFLICT` upserts, PL/pgSQL triggers, SQL functions, views, partial unique indexes (`one_equipped_per_save`).
- **Rejected:** MySQL (no deferrable FKs, weaker CHECK history, no partial indexes); SQLite (no real concurrency, limited types, no PL language).
- **Tools:** `psql` for scripts, pgAdmin for browsing tables during the viva.
- **Databases:** `shardbound` (game), `shardbound_test` (integration tests).

---

## 4. Frontend

### React + Vite (JavaScript)
- **Why:** React renders a single `GameView` object; `useReducer` in `GameContext` stores the latest one. Vite starts instantly and proxies `/api` to Javalin (S5).
- **JavaScript, not TypeScript:** the frontend is a renderer only; the course grades Java, so a second typed language adds effort without marks.
- **Rule:** no game logic in React (CLAUDE.md rule 1).

### Framer Motion
- **Why:** declarative animations for toasts, HP bars, damage numbers and region crossfades without hand-written animation timing code.

### Plain CSS with variables
- **Why:** region themes are just `body[data-region]` / `body[data-location]` selectors that swap CSS custom properties. No build-time CSS framework to explain.
- **Rejected:** Tailwind / CSS-in-JS: extra tooling with no grading value.

---

## 5. Development environment

| Tool | Required | Dev machine status (2026-11-18) |
|---|---|---|
| JDK ≥ 21 | yes | JDK 25.0.2 ✅ — set `JAVA_HOME` |
| Maven | no (wrapper downloads it) | wrapper generated ✅ |
| Node ≥ 20, npm | yes | Node 24.15.0, npm 11.12.1 ✅ |
| PostgreSQL 16 + psql | yes (from Phase 1) | to be installed by the student |
| git | yes | 2.55 ✅ |

### First-time setup (Windows, PowerShell)
```powershell
# 1. JDK: point JAVA_HOME at your JDK (new terminals pick it up)
setx JAVA_HOME "C:\Program Files\Java\jdk-25.0.2"

# 2. PostgreSQL 16: install, then add C:\Program Files\PostgreSQL\16\bin to PATH
winget install PostgreSQL.PostgreSQL.16

# 3. Secrets: copy the example and fill in your password
copy backend\src\main\resources\db.properties.example backend\src\main\resources\db.properties

# 4. Backend build + tests
cd backend; .\mvnw.cmd test

# 5. Frontend
cd frontend; npm install; npm run dev
```

### Ports
| Service | Port |
|---|---|
| Javalin API | 7070 |
| Vite dev server | 5173 (proxies `/api` → 7070) |
| PostgreSQL | 5432 |
