# Shardbound — Class Diagram v2

Classes for v2 (V2_SPEC §3–§5). Rule classes kept from v1 (rules, quest, story, item, event, achievement, combat) are described in `class-diagram.md` and appear here as plain boxes only. Signatures are indicative; the code is authoritative.
Legend: `+` public, `-` private, `~` package-private, `$` static. Two-parameter generics are written `Map‹K, V›`.

---

## 1. Engine, ports and persistence (P0)

The engine is framework-free (ArchUnit). It reaches the database only through the ports in `engine/port/`, which `persistence/` implements with `JdbcClient` and hand-written SQL.

```mermaid
classDiagram
    direction LR
    class GameEngine {
        -SaveStore saves
        -MetaStore meta
        -RunStore runs
        +newGame(String sessionToken, int playerId) SessionView
        +state(String sessionToken) SessionView
        +choose(String sessionToken, int choiceId) SessionView
        +combatAction(String sessionToken, Action a) SessionView
        +save(String sessionToken, int slot)
        +load(String sessionToken, int slot) SessionView
        -writeThrough(GameState s)
    }
    class SaveStore {
        <<interface, engine.port>>
        +save(int playerId, int slot, SaveSnapshot s)
        +load(int playerId, int slot) Optional~SaveSnapshot~
        +list(int playerId) List~SaveSummary~
        +delete(int playerId, int slot)
    }
    class MetaStore {
        <<interface, engine.port>>
        +unlockedAchievements(int playerId) Set~String~
        +unlockAchievement(int playerId, String id)
        +recordEnding(int playerId, String endingId, int seconds)
        +leaderboard() List~LeaderboardRow~
    }
    class RunStore {
        <<interface, engine.port>>
        +create(UUID runId, int playerId, byte[] tokenHash, SaveSnapshot s)
        +find(byte[] tokenHash) Optional~StoredRun~
        +update(UUID runId, long expectedVersion, SaveSnapshot s) long
    }
    class SaveRepository {
        <<persistence, JdbcClient>>
    }
    class MetaRepository {
        <<persistence, JdbcClient>>
    }
    class ActiveRunRepository {
        <<persistence, JdbcClient>>
    }
    class PlayerRepository {
        <<persistence, JdbcClient>>
        +findOrCreate(String username) int
    }
    class GameState {
        -Random rng
        +reseedForSave()
        +toSnapshot() SaveSnapshot
        +fromSnapshot(SaveSnapshot s, GameContent c)$ GameState
    }
    class RunCache {
        <<Caffeine, bounded, expire after access>>
        +get(String sessionToken) GameState
    }
    class AchievementManager

    GameEngine --> SaveStore
    GameEngine --> MetaStore
    GameEngine --> RunStore
    GameEngine --> RunCache
    GameEngine ..> GameState : synchronized(state)
    AchievementManager --> MetaStore
    SaveStore <|.. SaveRepository
    MetaStore <|.. MetaRepository
    RunStore <|.. ActiveRunRepository
```

`RunStore.update` returns the new version; a stale `expectedVersion` updates zero rows and the engine throws `RunConflictException` (409 `RUN_CONFLICT`).

---

## 2. Web layer (P0)

```mermaid
classDiagram
    direction LR
    class GameController {
        <<RestController>>
    }
    class CombatController {
        <<RestController>>
    }
    class SaveController {
        <<RestController>>
    }
    class ProfileController {
        <<RestController>>
    }
    class MetaController {
        <<RestController>>
    }
    class WorldController {
        <<RestController, V3>>
        +exit(ExitEvent e) SessionView
        +interact(InteractEvent e) SessionView
        +encounter(EncounterEvent e) SessionView
    }
    class InspectorController {
        <<RestController, dev profile>>
    }
    class SessionArgumentResolver {
        <<HandlerMethodArgumentResolver>>
        +resolveArgument(…) Session
    }
    class ProblemDetailAdvice {
        <<RestControllerAdvice>>
        +handle(GameException e) ProblemDetail
    }
    class ShardboundProperties {
        <<ConfigurationProperties "shardbound">>
    }
    class GameEngine

    GameController --> GameEngine
    CombatController --> GameEngine
    SaveController --> GameEngine
    WorldController --> GameEngine
    GameController ..> SessionArgumentResolver : X-Session (cookie from V2a)
    ProblemDetailAdvice ..> GameController : maps exceptions to code
```

---

## 3. Saves and content (P0)

```mermaid
classDiagram
    direction LR
    class SaveSnapshot {
        <<record, save>>
        int saveVersion
        String areaId
        int level
        int playtimeSeconds
        long rngSeed
        CombatSnapshot combat
    }
    class CombatSnapshot {
        <<record, save>>
        List enemies
        int round
        List~String~ log
    }
    class SaveUpgrader {
        -SortedMap‹Integer, SaveUpgrade› upgrades
        +CURRENT_VERSION$ int
        +upgrade(JsonNode doc, int fromVersion) JsonNode
    }
    class SaveUpgrade {
        <<interface>>
        +fromVersion() int
        +apply(ObjectNode doc) ObjectNode
    }
    class ContentLoader {
        +load(Path or classpath root) GameContent
        -validate(file, schema) List~SchemaError~
    }
    class JsonContentReader {
        +read(root) RawContent
    }
    class ContentAssembler {
        +assemble(RawContent raw) GameContent
    }
    class GameContent {
        +prepareForPlay()
    }
    class MapLoader {
        <<V3>>
        +load(String areaId) MapData
    }
    class MapValidator {
        <<V3>>
        +validate(MapData m) List~String~
    }

    SaveSnapshot *-- CombatSnapshot
    SaveUpgrader o-- SaveUpgrade
    ContentLoader --> JsonContentReader
    ContentLoader --> ContentAssembler
    ContentAssembler ..> GameContent
    ContentLoader ..> MapLoader
    MapLoader ..> MapValidator : BFS reachability
```

---

## 4. Accounts, sessions, email (V2a / V2b)

```mermaid
classDiagram
    direction LR
    class AccountService {
        +createGuest(String ip, String turnstile) Session
        +upgradeGuestWithGoogle(int accountId, OidcUser u)
        +deleteAccount(int accountId)
        +export(int accountId) AccountExport
    }
    class GoogleLoginHandler {
        <<AuthenticationSuccessHandler>>
        +onAuthenticationSuccess(…)
        -linkOnlyIfBothEmailsVerified(OidcUser u, Account a) boolean
    }
    class SessionService {
        <<SecurityContextRepository>>
        +create(int accountId) String
        +resolve(String token) Session
        +revoke(String token)
        +revokeAll(int accountId, String exceptToken)
    }
    class PasswordService {
        <<V2b>>
        -Argon2PasswordEncoder encoder
        +hash(CharSequence password) String
        +matches(CharSequence password, String phc) boolean
        +needsRehash(String phc) boolean
    }
    class TokenService {
        <<V2b>>
        +issue(int accountId, Purpose p, Duration ttl, String newEmail) String
        +consume(String token, Purpose p) TokenRow
    }
    class SlidingWindowRateLimiter {
        -ConcurrentHashMap‹String, ArrayDeque‹Long›› windows
        -int limit
        -long windowMillis
        +tryAcquire(String key, long nowMillis) boolean
        +remaining(String key, long nowMillis) int
    }
    class LoginGuard {
        -SlidingWindowRateLimiter perIp
        -SlidingWindowRateLimiter perEmail
        +check(String ip, String email)
        +recordFailure(String email)
    }
    class TurnstileVerifier {
        +verify(String token, String ip) boolean
    }
    class SecurityConfig {
        <<Configuration>>
        +filterChain(HttpSecurity http) SecurityFilterChain
    }
    class AccountCleanupJob {
        <<Scheduled, hourly>>
        +runOnce()
    }
    class EmailSender {
        <<interface, V2b>>
        +send(EmailMessage m)
    }
    class SmtpEmailSender
    class EmailOutboxWorker {
        <<V2b>>
        +pollOnce() int
        -backoff(int attempts) Duration
    }
    class EmailTemplates {
        +render(String template, Map vars) EmailMessage
    }

    SecurityConfig ..> SessionService
    SecurityConfig ..> GoogleLoginHandler
    GoogleLoginHandler --> AccountService
    AccountService --> SessionService
    AccountService --> TurnstileVerifier
    AccountService --> PasswordService
    AccountService --> TokenService
    AccountService --> LoginGuard
    AccountService ..> EmailOutboxWorker : inserts outbox row in same tx
    LoginGuard --> SlidingWindowRateLimiter
    EmailSender <|.. SmtpEmailSender
    EmailOutboxWorker --> EmailSender
    EmailOutboxWorker --> EmailTemplates
```

---

## 5. World and world events (V3)

```mermaid
classDiagram
    direction LR
    class WorldEngine {
        +snapshot(GameState s) WorldView
        +exit(GameState s, String exitId, Position p) SessionView
        +interact(GameState s, String targetId, Position p) SessionView
        +encounter(GameState s, String creepId, Position p) SessionView
    }
    class WorldEventValidator {
        +validate(GameState s, Position p, Target t, long nowMillis) Verdict
        -walkable(MapData m, int tx, int ty) boolean
        -pathLength(MapData m, Tile from, Tile to) int
        -withinSpeed(int tiles, long elapsedMillis) boolean
        -inRange(Tile at, Target t) boolean
    }
    class CollisionGrid {
        -BitSet bits
        -int width
        -int height
        +blocked(int x, int y) boolean
        +neighbours(int x, int y) List
    }
    class MapData {
        <<record>>
        String areaId
        CollisionGrid collision
        List~MapObject~ objects
    }
    class AreaGraph {
        -Graph‹String, Integer› graph
        +route(String from, String to, Set gateFlags) Route
        +nextHop(String from, String to) String
    }
    class EncounterService {
        -SpatialHash spatial
        +aggroAt(GameState s, int x, int y) CreepSpawn
    }
    class SpatialHash {
        -HashMap‹Long, List‹CreepSpawn›› cells
        +near(int x, int y) List~CreepSpawn~
        -key(int cx, int cy)$ long
    }
    class CreepSpawner {
        +respawnAll(GameState s)
        +levelFor(CreepSpawn spawn, Random rng) int
    }
    class Graph~V~ {
        <<generic Graph‹V, E›, ds>>
        +bfs(V start) List~V~
        +dfs(V start) List~V~
        +shortestPath(V from, V to) List~V~
        +dijkstra(V source, ToIntFunction w) ShortestPaths
    }

    WorldEngine --> WorldEventValidator
    WorldEngine --> EncounterService
    WorldEngine --> CreepSpawner
    WorldEngine --> MapData
    WorldEventValidator --> CollisionGrid : BFS + speed check
    MapData *-- CollisionGrid
    EncounterService --> SpatialHash
    AreaGraph --> Graph : dijkstra
```

---

## 6. Battle (V4, V7)

```mermaid
classDiagram
    direction LR
    class BattleEngine {
        +start(GameState s, Opponent o) BattleState
        +act(GameState s, BattleAction a) CombatOutcome
        -turnOrder(BattleState b) PriorityQueue
    }
    class BattleState {
        -Fighter player
        -List~Fighter~ enemies
        -ArrayDeque~String~ text
        -TelegraphQueue telegraphs
        -boolean bossFight
        -int round
    }
    class Fighter {
        -String name
        -int level
        -int hp
        -int maxHp
        -int def
        -int spd
        -String family
        -StatusTracker statuses
        -boolean guarding
    }
    class DamageCalculator {
        +roll(Fighter a, Fighter d, Move m, double power, Random rng) DamageRoll
        +attackRating(WeaponInstance w, Attributes a) double
        +spellPower(Attributes a, Scaling s) double
    }
    class ElementChart {
        -EnumMap‹Element, EnumMap‹Family, Double›› chart
        +multiplier(String family, Element e) double
    }
    class StatusTracker {
        -EnumMap‹Status, Integer› turnsLeft
        +apply(Status s, int turns)
        +tick(Fighter f) List~String~
        +has(Status s) boolean
    }
    class Skill {
        <<record>>
        String id
        Element element
        double power
        int fpCost
        Scaling scaling
        Status status
        int statusChance
    }
    class BossAI {
        -BossScript script
        -int phase
        +decide(BattleState b, Random rng) BossMove
    }
    class BossScript {
        <<record>>
        List~BossPhase~ phases
        String introCutscene
    }
    class TelegraphQueue {
        -ArrayDeque~BossMove~ announced
        +announce(BossMove m)
        +next() BossMove
    }

    BattleEngine --> DamageCalculator
    BattleEngine --> ElementChart
    BattleEngine ..> BattleState
    BattleState *-- Fighter
    BattleState *-- TelegraphQueue
    Fighter *-- StatusTracker
    BattleEngine --> BossAI
    BossAI --> BossScript
    DamageCalculator ..> Skill
```

---

## 7. Progression, gear, dialogue, cutscenes (V5–V8)

```mermaid
classDiagram
    direction LR
    class Attributes {
        -EnumMap‹Attribute, Integer› values
        +level() int
        +raise(Attribute a, int by)
        +maxHp(Curves c) int
        +maxFp(Curves c) int
        +defence(Curves c) int
    }
    class Curve {
        -TreeMap‹Integer, Double› points
        +valueAt(int x) double
    }
    class LevelCost {
        +cost(int level)$ int
        +creepShards(int level)$ int
        +bossShards(int level)$ int
    }
    class LanternService {
        +rest(GameState s) SessionView
        +preview(GameState s, Map raises) LevelUpView
        +levelUp(GameState s, Map raises) SessionView
        +travel(GameState s, String lanternId) SessionView
    }
    class LostShards {
        <<record>>
        String areaId
        int x
        int y
        int amount
    }
    class WeaponInstance {
        -Weapon weapon
        -int upgrade
        +grade(Attribute a) ScalingGrade
    }
    class UpgradeService {
        +upgrade(GameState s, String itemId) SessionView
        +costOf(int fromLevel) MaterialCost
    }
    class DialogueEngine {
        +start(GameState s, String dialogueId) DialogueView
        +choose(GameState s, int choiceId) SessionView
    }
    class DialogueGraph {
        -HashMap‹String, DialogueNode› nodes
        -HashMap‹String, List‹Choice›› choices
    }
    class CutscenePlayer {
        +play(GameState s, String id) CutsceneView
        +skip(GameState s) SessionView
    }
    class CutsceneStep {
        <<sealed interface>>
    }
    class ConditionGroup
    class Effect

    Attributes ..> Curve
    LanternService --> Attributes
    LanternService --> LevelCost
    LanternService ..> LostShards
    UpgradeService ..> WeaponInstance
    DialogueEngine --> DialogueGraph
    DialogueGraph ..> ConditionGroup : reused from v1
    DialogueGraph ..> Effect : reused from v1
    CutscenePlayer ..> CutsceneStep
```

---

## 8. Telemetry, tools, dev (V9–V10)

```mermaid
classDiagram
    direction LR
    class TelemetryRecorder {
        -LinkedBlockingQueue~TelemetryEvent~ queue
        -Thread writer
        +record(TelemetryEvent e)
        -flushBatch()
    }
    class BalanceSimulator {
        +main(String[] args)$
        +simulate(String opponent, int level, Build b, int runs) SimReport
    }
    class PlaytestBot {
        +main(String[] args)$
        +playRegion(String regionId) RegionReport
    }
    class ChapterSelectController {
        <<RestController, dev profile>>
    }
    class BattleEngine
    class WorldController

    BalanceSimulator ..> BattleEngine : headless, seeded
    PlaytestBot ..> WorldController : real REST API, paced for the speed check
```
