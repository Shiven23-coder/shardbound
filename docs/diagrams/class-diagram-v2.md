# Shardbound — Class Diagram v2

New and changed classes for v2 (V2_SPEC §3–§5). Unchanged v1 classes (rules, quest, event, achievement, item, persistence, api) are described in `class-diagram.md` and appear here as plain boxes only.
Legend: `+` public, `-` private, `~` package-private, `$` static. ✍ = student-implemented (CLAUDE.md rule 8). Two-parameter generics are written `Map‹K, V›`.

---

## 1. Accounts, sessions, email

```mermaid
classDiagram
    direction LR
    class AuthService {
        +signup(SignupRequest r, String ip) void
        +login(String email, String password, String ip) Session
        +logout(String token)
        +createGuest(String ip, String turnstile) Session
        +upgradeGuest(int accountId, UpgradeRequest r)
        +verifyEmail(String token)
        +resendVerification(int accountId)
        +forgotPassword(String email, String turnstile, String ip)
        +resetPassword(String token, String newPassword)
        +changePassword(int accountId, String current, String next)
        +changeEmail(int accountId, String password, String newEmail)
        +deleteAccount(int accountId, String password)
    }
    class PasswordHasher {
        <<Argon2id, Bouncy Castle>>
        -int memoryKiB = 19456
        -int iterations = 2
        -int parallelism = 1
        +hash(char[] password) String
        +verify(char[] password, String phc) boolean
        +needsRehash(String phc) boolean
    }
    class SessionService {
        +create(int accountId) String
        +resolve(String token) Session
        +revoke(String token)
        +revokeAll(int accountId, String exceptToken)
        -sha256(String token) byte[]
    }
    class TokenService {
        +issue(int accountId, Purpose p, Duration ttl, String newEmail) String
        +consume(String token, Purpose p) TokenRow
    }
    class SlidingWindowRateLimiter {
        -ConcurrentHashMap‹String, ArrayDeque‹Long›› windows
        -int limit
        -long windowMillis
        +tryAcquire(String key, long nowMillis) boolean ✍
        +remaining(String key, long nowMillis) int ✍
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
    class SecurityHeaders {
        +register(Javalin app)$
    }
    class OriginCheck {
        +register(Javalin app, String appOrigin)$
    }
    class AccountCleanupJob {
        -ScheduledExecutorService scheduler
        +runOnce()
    }
    class EmailSender {
        <<interface>>
        +send(EmailMessage m)
    }
    class SmtpEmailSender
    class MailpitEmailSender
    class EmailOutboxWorker {
        -EmailSender sender
        -EmailTemplates templates
        +pollOnce() int
        -backoff(int attempts) Duration
    }
    class EmailTemplates {
        +render(String template, Map vars) EmailMessage
    }
    class Purpose {
        <<enumeration>>
        VERIFY
        RESET
        CHANGE_EMAIL
    }

    AuthService --> PasswordHasher
    AuthService --> SessionService
    AuthService --> TokenService
    AuthService --> LoginGuard
    AuthService --> TurnstileVerifier
    AuthService ..> EmailOutboxWorker : inserts outbox row in same tx
    LoginGuard --> SlidingWindowRateLimiter
    TokenService --> Purpose
    EmailSender <|.. SmtpEmailSender
    EmailSender <|.. MailpitEmailSender
    EmailOutboxWorker --> EmailSender
    EmailOutboxWorker --> EmailTemplates
```

---

## 2. World and movement

```mermaid
classDiagram
    direction LR
    class WorldEngine {
        +snapshot(GameState s) WorldView
        +move(GameState s, int seq, Dir dir) MoveResult
        +interact(GameState s) SessionView
        -checkTriggers(GameState s) TriggerResult
    }
    class MovementValidator {
        -SlidingWindowRateLimiter steps
        +validate(GameState s, Dir dir, long now) Verdict
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
    class WorldSocket {
        <<Javalin WS handler>>
        +onConnect(WsConnectContext c)
        +onMessage(WsMessageContext c)
        +onClose(WsCloseContext c)
        -handshake(WsConnectContext c) boolean
    }
    class Graph~V~ {
        <<generic Graph‹V, E›, ds>>
        +bfs(V start) List~V~ ✍
        +dfs(V start) List~V~ ✍
        +shortestPath(V from, V to) List~V~ ✍
        +dijkstra(V source, ToIntFunction w) ShortestPaths ✍
    }

    WorldSocket --> WorldEngine : synchronized(state)
    WorldEngine --> MovementValidator
    WorldEngine --> EncounterService
    WorldEngine --> CreepSpawner
    WorldEngine --> MapData
    MovementValidator --> CollisionGrid
    MapData *-- CollisionGrid
    EncounterService --> SpatialHash
    AreaGraph --> Graph : dijkstra
```

---

## 3. Battle

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
    class Element {
        <<enumeration>>
        PHYSICAL
        FIRE
        FROST
        LIGHTNING
        HOLY
        CRYSTAL
    }
    class Status {
        <<enumeration>>
        BLEED
        POISON
        BURN
        FROST
        STUN
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
    ElementChart --> Element
    StatusTracker --> Status
```

---

## 4. Progression, gear, dialogue, cutscenes

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
        +valueAt(int x) double ✍
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
    class ScalingGrade {
        <<enumeration>>
        S
        A
        B
        C
        D
        E
        NONE
        +coefficient double
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
    WeaponInstance --> ScalingGrade
    UpgradeService ..> WeaponInstance
    DialogueEngine --> DialogueGraph
    DialogueGraph ..> ConditionGroup : reused from v1
    DialogueGraph ..> Effect : reused from v1
    CutscenePlayer ..> CutsceneStep
```

---

## 5. Saves, telemetry, tools, dev

```mermaid
classDiagram
    direction LR
    class SaveMigrator {
        -TreeMap‹Integer, Migration› migrations
        +migrate(SaveData d) SaveData
        +CURRENT_VERSION$ int = 2
    }
    class Migration {
        <<interface>>
        +fromVersion() int
        +apply(SaveData d) SaveData
    }
    class V1toV2Migration
    class SaveV2Repository {
        +save(GameState s, int accountId, int slot)
        +load(int accountId, int slot) SaveData
    }
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
    class ChapterSelect {
        <<dev only>>
        +register(Javalin app, AppConfig c)$
    }

    Migration <|.. V1toV2Migration
    SaveMigrator o-- Migration
    SaveV2Repository ..> SaveMigrator : on load
    BalanceSimulator ..> BattleEngine : headless, seeded
    PlaytestBot ..> WorldSocket : real API
    class BattleEngine
    class WorldSocket
```
