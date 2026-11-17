# Shardbound — Class Diagram

Source for `docs/class-diagram.png` (exported in Phase 10). One diagram per area so each stays readable.
Matches spec §3, §5 and §6 including decisions D4, D6, D7, D9, D10, D14, D15, D16 and D20–D31.

Legend: `+` public, `-` private, `#` protected, `$` static, `*` abstract. ✍ = student-implemented (CLAUDE.md rule 8).
Generic types with two parameters are written `Map‹K, V›` inside member text, because Mermaid cannot draw a comma inside `~generic~` markers.

Design notes that the diagrams rely on:
- **One `EventBus` per session.** It is created with its `GameState`, so `publish(event)` keeps the spec's one-argument signature and listeners still receive the state. The engine registers a lambda listener that turns each event into a `Toast`.
- **`Ending`, `Step` and `Toast`** are small records in `engine` (the spec's data-structure table already uses them).
- **Controllers call only `GameEngine` for gameplay** (D21, D22); `GameEngine` takes the state lock.
- **Content is immutable and shared; per-session objects are created per session or per fight** (D20, D23).
- **`GameState` / `CombatState` fields are private** (D30). `~` before a member means package-private.

---

## 1. Engine, sessions and API

```mermaid
classDiagram
    direction LR

    class App {
        +main(String[] args)$
    }
    class AppConfig {
        +int port
        +boolean simulateSaveCrash
        +String dbUrl
        +String dbUser
        +String dbPassword
        +load(String resource)$ AppConfig
    }
    class Database {
        -AppConfig config
        +getConnection() Connection
    }

    class GameController {
        +register(Javalin app)
    }
    class CombatController {
        +register(Javalin app)
    }
    class SaveController {
        +register(Javalin app)
    }
    class ProfileController {
        +register(Javalin app)
    }
    class MetaController {
        +register(Javalin app)
    }
    class ErrorHandler {
        +register(Javalin app)
    }

    class SessionStore {
        -ConcurrentHashMap‹String, GameState› sessions
        +create(GameState s) String
        +get(String sessionId) GameState
        +remove(String sessionId)
    }

    class GameEngine {
        -StoryGraph graph
        -CombatEngine combatEngine
        -QuestManager questManager
        -EndingResolver endingResolver
        -SaveRepository saves
        -MetaRepository meta
        -ExecutorService autosaver
        +newGame(int playerId, boolean demoMode) GameState
        +view(GameState s) GameView
        +choose(GameState s, int choiceId) GameView
        +combatAction(GameState s, Action a) GameView
        +equip(GameState s, String itemId) GameView
        +use(GameState s, String itemId) GameView
        +save(GameState s, int slot)
        +load(int playerId, int slot) GameState
        +listSaves(int playerId) List
        +deleteSave(int playerId, int slot)
        -enterNode(GameState s, String nodeId) GameView
        -leaveCombat(GameState s, String nodeId) GameView
        -finish(GameState s, StoryNode n) GameView
        -autosave(GameState s)
        -wireSession(GameState s)
        -rebuild(SaveData d) GameState
    }

    class GameState {
        -String sessionId
        -int playerId
        -String currentNode
        -Mode mode
        -Stats stats
        -Inventory inventory
        -HashMap‹String, Integer› flags
        -EnumMap‹Faction, Integer› reputation
        -HashMap‹String, Integer› affinity
        -HashMap‹String, QuestStatus› quests
        -ArrayDeque~Step~ history
        -CombatState combat
        -long rngSeed
        -Random rng
        -long sessionStartMillis
        -int playSecondsBefore
        -ArrayDeque~Toast~ pendingToasts
        -boolean demoMode
        -EventBus bus
        +flag(String key) int
        +setFlag(String key, int value)
        +reputation(Faction f) int
        +addReputation(Faction f, int delta)
        +affinity(String npcId) int
        +addAffinity(String npcId, int delta)
        +questStatus(String questId) QuestStatus
        +setQuestStatus(String questId, QuestStatus st)
        +addToast(Toast t)
        +flagsView() Map
        +reputationView() Map
        +affinityView() Map
        +questsView() Map
        +historyOldestFirst() List~Step~
        +reseedForSave() long
        +deepCopy() GameState
        +playSeconds() int
        ~historyStack() Deque~Step~
        ~toastQueue() Queue~Toast~
    }

    class Mode {
        <<enumeration>>
        STORY
        COMBAT
        ENDED
    }

    class EndingResolver {
        -List~Ending~ endings
        +resolve(GameState s) Ending
    }
    class Ending {
        <<record>>
        String id
        String nodeId
        String title
        int priority
        boolean viaResolver
        ConditionGroup conditions
    }
    class Step {
        <<record>>
        String nodeId
        int choiceId
    }
    class Toast {
        <<record>>
        String kind
        String text
        +from(GameEvent e)$ Toast
    }

    class GameView {
        <<record>>
        String sessionId
        Mode mode
        String region
        String location
        NodeInfo node
        List~ChoiceView~ choices
        HudView hud
        List~ToastView~ toasts
        CombatView combat
        EndingInfo ending
        boolean offerReload
    }
    class ChoiceView {
        <<record>>
        int id
        String label
        boolean locked
        String lockReason
    }
    class HudView {
        <<record>>
        int hp
        int maxHp
        int energy
        int maxEnergy
        int gold
        int level
        int xp
        int xpToNext
        WeaponInfo weapon
    }
    class CombatView {
        <<record>>
        List~EnemyInfo~ enemies
        List~String~ log
        boolean canFlee
        List~String~ usableItems
        List~HitInfo~ lastHits
    }
    class ToastView {
        <<record>>
        String kind
        String text
    }

    App --> AppConfig
    App --> Database
    App --> GameEngine
    App --> SessionStore
    GameController --> GameEngine
    GameController --> SessionStore
    CombatController --> GameEngine : only (D21)
    SaveController --> GameEngine : only (D22)
    SaveController --> SessionStore
    ProfileController --> PlayerRepository
    MetaController --> MetaRepository
    SessionStore o-- GameState
    GameEngine ..> GameState : mutates under synchronized(s)
    GameEngine --> SaveRepository
    GameEngine ..> AchievementManager : creates one per session (D23)
    GameEngine --> EndingResolver
    GameEngine ..> GameView : builds
    EndingResolver o-- Ending
    GameState --> Mode
    GameState *-- Step
    GameState *-- Toast
    GameView *-- ChoiceView
    GameView *-- HudView
    GameView *-- CombatView
    GameView *-- ToastView

    class SaveRepository
    class PlayerRepository
    class MetaRepository
    class AchievementManager
```

---

## 2. Story graph and generic graph

```mermaid
classDiagram
    direction LR

    class Graph~V~ {
        <<generic Graph‹V, E›>>
        -HashMap‹V, List‹Edge‹V, E››› adjacency
        +addVertex(V v)
        +addEdge(V from, V to, E value)
        +neighbors(V v) List~V~
        +vertices() Set~V~
        +bfs(V start) List~V~ ✍
        +dfs(V start) List~V~ ✍
        +shortestPath(V from, V to) List~V~ ✍
    }
    class Edge~V~ {
        <<record Edge‹V, E›>>
        V to
        E value
    }

    class StoryNode {
        <<record>>
        String id
        int chapter
        String locationId
        NodeType type
        String title
        String body
        String encounterId
        String winNode
        String loseNode
        String fleeNode
    }
    class NodeType {
        <<enumeration>>
        STORY
        COMBAT
        RESOLVER
        ENDING
    }
    class Choice {
        <<record>>
        int id
        String from
        String to
        String label
        int sortOrder
        boolean hiddenWhenLocked
        ConditionGroup conditions
        List~Effect~ effects
        +hasRepOrAffinityCondition() boolean
    }

    class StoryGraph {
        -HashMap‹String, StoryNode› nodes
        -HashMap‹String, List‹Choice›› choicesByNode
        -Graph‹String, String› structure
        +node(String id) StoryNode
        +choice(String nodeId, int choiceId) Optional~Choice~
        +choicesFrom(String nodeId) List~Choice~
        +structure() Graph
        -addStructuralEdges(List~Ending~ endings)
    }

    class StoryValidator {
        -StoryGraph graph
        -List~String~ errors
        -List~String~ warnings
        +validate() boolean
        +errors() List~String~
        +warnings() List~String~
        -checkReachable()
        -checkDeadEnds()
        -checkCombatNodes()
        -checkEndingsReachable()
        -checkQuickRouteLength()
        -checkReferences()
        -checkFlagsReadNeverSet()
        -checkFlagsSetNeverRead()
        -checkReservedFlags()
        -checkQuestTransitions()
    }

    Graph *-- Edge
    StoryGraph *-- StoryNode
    StoryGraph *-- Choice
    StoryGraph --> Graph : D10 structural edges
    StoryNode --> NodeType
    StoryValidator --> StoryGraph
    StoryValidator ..> Graph : bfs, dfs, shortestPath
```

---

## 3. Rules: conditions and effects

```mermaid
classDiagram
    direction TB

    class Condition {
        <<sealed interface>>
        +test(GameState s) boolean
        +describe() String
    }
    class FlagCondition {
        <<record>>
        String key
        Op op
        int value
    }
    class RepCondition {
        <<record>>
        Faction faction
        Op op
        int value
    }
    class AffinityCondition {
        <<record>>
        String npcId
        Op op
        int value
    }
    class ItemCondition {
        <<record>>
        String itemId
        Op op
        int value
    }
    class QuestCondition {
        <<record>>
        String questId
        Op op
        QuestStatus status
    }
    class StatCondition {
        <<record>>
        String stat
        Op op
        int value
    }
    class Op {
        <<enumeration>>
        EQ
        NE
        GT
        GE
        LT
        LE
        +compare(int actual, int expected) boolean
        +fromSymbol(String s)$ Op
    }
    class ConditionGroup {
        -TreeMap‹Integer, List‹Condition›› groups
        +test(GameState s) boolean
        +describe() String
        +isEmpty() boolean
        +empty()$ ConditionGroup
    }

    class Effect {
        <<interface>>
        +apply(GameState s, EventBus bus)
    }
    class SetFlagEffect
    class RepEffect
    class AffinityEffect
    class GiveItemEffect
    class TakeItemEffect
    class StartQuestEffect
    class CompleteQuestEffect
    class FailQuestEffect
    class HealEffect
    class GoldEffect
    class XpEffect

    class RuleFactory {
        +condition(String type, String key, String op, int value)$ Condition
        +effect(String type, String key, int value)$ Effect
    }

    Condition <|.. FlagCondition
    Condition <|.. RepCondition
    Condition <|.. AffinityCondition
    Condition <|.. ItemCondition
    Condition <|.. QuestCondition
    Condition <|.. StatCondition
    ConditionGroup o-- Condition : grp → AND list (D4)
    FlagCondition --> Op
    Effect <|.. SetFlagEffect
    Effect <|.. RepEffect
    Effect <|.. AffinityEffect
    Effect <|.. GiveItemEffect
    Effect <|.. TakeItemEffect
    Effect <|.. StartQuestEffect
    Effect <|.. CompleteQuestEffect
    Effect <|.. FailQuestEffect
    Effect <|.. HealEffect
    Effect <|.. GoldEffect
    Effect <|.. XpEffect
    RuleFactory ..> Condition : creates
    RuleFactory ..> Effect : creates
```

Every effect class holds `key` and/or `value` fields and publishes one `GameEvent` from `apply`. Quest effects delegate to `QuestManager`, which logs and ignores invalid transitions (D14).

---

## 4. Model and items

```mermaid
classDiagram
    direction LR

    class Faction {
        <<enumeration>>
        ELDMERE
        SYLVAN
        CROWN
        ASHEN
        +String displayName
    }
    class Npc {
        <<record>>
        String id
        String name
        Faction faction
        String locationId
    }
    class Stats {
        -int hp
        -int energy
        -int level
        -int xp
        -int gold
        +maxHp() int
        +atk() int
        +def() int
        +spd() int
        +maxEnergy() int
        +xpToNext() int
        +addXp(int amount) boolean
        +heal(int amount)
        +addGold(int amount)
        +refillEnergy()
    }
    class Tier {
        <<enumeration>>
        F
        E
        D
        C
        B
        A
        S
        SS
        SSS
        +double multiplier
        +BY_RANK_DESC$ Comparator~Tier~
    }

    class Item {
        <<abstract>>
        #String id
        #String name
        #String description
        #int price
        +isStackable()* boolean
    }
    class Weapon {
        -Tier tier
        -int attack
        -int critPct
        -String ability
        +isStackable() boolean
    }
    class Consumable {
        -int heal
        -int energy
        +isStackable() boolean
    }
    class KeyItem {
        +isStackable() boolean
    }
    class ItemStack~T~ {
        <<generic, T extends Item>>
        -T item
        -int qty
        +add(int n)
        +remove(int n)
    }
    class Inventory {
        +CAPACITY$ int = 20
        -LinkedHashMap‹String, ItemStack‹Item›› stacks
        -String equippedWeaponId
        +add(Item item, int qty)
        +remove(String itemId, int qty)
        +count(String itemId) int
        +equip(String weaponId)
        +equippedWeapon() Weapon
        +weaponsByTier() List~Weapon~
        +usedSlots() int
    }

    Npc --> Faction
    Item <|-- Weapon
    Item <|-- Consumable
    Item <|-- KeyItem
    Weapon --> Tier
    ItemStack o-- Item
    Inventory *-- ItemStack
```

`Stats` derives `maxHp` (`40 + 5 * (level − 1)`), `atk`, `def`, `spd` and `maxEnergy` from `level` (D6, D28). `xp` is progress within the current level; `addXp` levels up while `xp ≥ xpToNext` and keeps the leftover (D29). `Inventory.add` throws `InventoryFullException` when a new stack would exceed 20 slots.

---

## 5. Quests and combat

```mermaid
classDiagram
    direction LR

    class Quest {
        <<record>>
        String id
        String title
        String description
        String giverNpc
        String rewardItem
        int rewardXp
        int rewardGold
        boolean isMain
        List~String~ prereqs
    }
    class QuestStatus {
        <<enumeration>>
        LOCKED
        AVAILABLE
        ACTIVE
        COMPLETED
        FAILED
    }
    class QuestManager {
        -HashMap‹String, Quest› quests
        -List~String~ order
        +topoOrder() List~String~ ✍
        +initialStates() HashMap
        +start(GameState s, String questId, EventBus bus)
        +complete(GameState s, String questId, EventBus bus)
        +fail(GameState s, String questId, EventBus bus)
        -refreshAvailability(GameState s)
    }

    class Combatant {
        <<abstract>>
        #String name
        #int hp
        #int maxHp
        #int atk
        #int def
        #int spd
        #boolean defending
        +isAlive() boolean
        +takeDamage(int amount)
        +isPlayer()* boolean
    }
    class PlayerCombatant {
        -Weapon weapon
        +isPlayer() boolean
    }
    class Enemy {
        -int index
        -int xpReward
        -int goldReward
        -EnemyAI ai
        -int turnsTaken
        +from(EnemyTemplate t, int index, boolean demoMode)$ Enemy
        +isPlayer() boolean
    }
    class EnemyTemplate {
        <<record>>
        String id
        String name
        int hp
        int atk
        int def
        int spd
        int xpReward
        int goldReward
        String ai
    }
    class CombatState {
        -String encounterId
        -PlayerCombatant player
        -List~Enemy~ enemies
        -ArrayDeque~String~ log
        -boolean canFlee
        -int round
        -List~Hit~ lastHits
        +encounterId() String
        +player() PlayerCombatant
        +enemiesView() List~Enemy~
        +logView() List~String~
        +canFlee() boolean
        +round() int
        +lastHitsView() List~Hit~
        ~addLog(String line)
        ~enemies() List~Enemy~
        ~setLastHits(List~Hit~ hits)
    }
    class CombatOutcome {
        <<enumeration>>
        ONGOING
        WON
        LOST
        FLED
    }
    class Action {
        <<record>>
        ActionType type
        int target
        String itemId
    }
    class ActionType {
        <<enumeration>>
        ATTACK
        DEFEND
        SKILL
        ITEM
        FLEE
    }
    class CombatEngine {
        -DamageCalculator calc
        -HashMap‹String, EnemyTemplate› templates
        -HashMap‹String, List‹EncounterSlot›› encounters
        +start(GameState s, String encounterId) CombatState
        +act(GameState s, Action a) CombatOutcome
        -runRound(GameState s, Action a)
        -turnOrder(CombatState c) PriorityQueue~Combatant~
        -fleeChance(CombatState c) double
        -onWin(GameState s)
        -onLose(GameState s)
        -onFlee(GameState s)
    }
    class DamageCalculator {
        +damage(Combatant atk, Combatant def, Weapon w, double skillMult, Random rng) Hit
    }
    class Hit {
        <<record>>
        int targetIndex
        int amount
        boolean crit
    }
    class EnemyAI {
        <<interface>>
        +decide(Enemy self, CombatState c) Action
    }
    class AggressiveAI
    class DefensiveAI {
        -boolean hasDefended
    }
    class BossAI {
        -boolean phaseTwo
    }

    QuestManager o-- Quest
    QuestManager ..> QuestStatus
    Combatant <|-- PlayerCombatant
    Combatant <|-- Enemy
    CombatState *-- PlayerCombatant
    CombatState *-- Enemy
    CombatState *-- Hit
    Action --> ActionType
    CombatEngine --> DamageCalculator
    CombatEngine ..> CombatState : runs rounds
    CombatEngine ..> Action
    CombatEngine ..> CombatOutcome : returns (D21)
    CombatEngine o-- EnemyTemplate : shared, immutable
    Enemy ..> EnemyTemplate : built from, per fight (D20)
    DamageCalculator ..> Hit : returns
    Enemy --> EnemyAI : strategy
    EnemyAI <|.. AggressiveAI
    EnemyAI <|.. DefensiveAI
    EnemyAI <|.. BossAI
```

Turn order: a `PriorityQueue<Combatant>` by speed descending; ties go to the player, then lower enemy index (S1). Energy refills at `start` (S2). `act` returns a `CombatOutcome`; `GameEngine.combatAction` routes WON → `win_node`, LOST → `lose_node`, FLED → `flee_node` or `lose_node`, no penalty for fleeing (D9, D21). `EncounterSlot` is a small record `(String enemyId, int qty)` from `encounter_enemy`.

---

## 6. Events, achievements, persistence, tools and exceptions

```mermaid
classDiagram
    direction LR

    class GameEvent {
        <<record>>
        EventType type
        Map data
    }
    class EventType {
        <<enumeration>>
        NODE_ENTERED
        ITEM_GAINED
        ITEM_EQUIPPED
        REP_CHANGED
        AFFINITY_CHANGED
        QUEST_STARTED
        QUEST_COMPLETED
        QUEST_FAILED
        COMBAT_WON
        COMBAT_LOST
        LOCKED_CHOICE_PASSED
        ENDING_REACHED
        LEVEL_UP
    }
    class GameListener {
        <<interface>>
        +onEvent(GameEvent e, GameState s)
    }
    class EventBus {
        -GameState owner
        -List~GameListener~ listeners
        +subscribe(GameListener l)
        +publish(GameEvent e)
    }

    class Achievement {
        <<record>>
        String id
        String title
        String description
        boolean hidden
    }
    class AchievementRule {
        <<interface>>
        +achievementId() String
        +matches(GameEvent e, GameState s) boolean
    }
    class AchievementManager {
        -EnumMap‹EventType, List‹AchievementRule›› RULES$
        -HashSet~String~ unlocked
        -int playerId
        -MetaRepository meta
        +AchievementManager(int playerId, Set~String~ alreadyUnlocked, MetaRepository meta)
        +onEvent(GameEvent e, GameState s)
    }

    class ContentRepository {
        +loadLocations() List~Location~
        +loadNpcs() List~Npc~
        +loadNodes() List~StoryNode~
        +loadChoices() List~Choice~
        +loadItems() List~Item~
        +loadEnemies() List~EnemyTemplate~
        +loadEncounters() Map
        +loadQuests() List~Quest~
        +loadEndings() List~Ending~
        +loadAchievements() List~Achievement~
    }
    class SaveRepository {
        +save(GameState s, int playerId, int slot) ✍
        +load(int playerId, int slot) SaveData
        +listSlots(int playerId) List
        +delete(int playerId, int slot)
    }
    class SaveData {
        <<record>>
        int saveId
        String currentNode
        int hp
        int energy
        int level
        int xp
        int gold
        long rngSeed
        boolean demoMode
        int playSeconds
        List~InventoryRow~ inventory
        Map flags
        Map reputation
        Map affinity
        Map quests
        List~HistoryRow~ history
    }
    class PlayerRepository {
        +findOrCreate(String username) int
    }
    class MetaRepository {
        +unlockAchievement(int playerId, String id) boolean
        +recordEnding(int playerId, String endingId, int seconds)
        +unlockedFor(int playerId) Set~String~
        +endingsFor(int playerId) Set~String~
        +leaderboard() List
    }
    class SqlLog {
        <<thread-safe>>
        -ArrayDeque~String~ recent$
        +record(String sql)$ synchronized
        +lastFive()$ List~String~ synchronized
    }

    class ContentImporter {
        +main(String[] args)$
        -readJson()
        -upsertAll(Connection c)
        -replaceChildRows(Connection c)
        -deleteRemoved(Connection c)
    }
    class ConsoleRunner {
        +main(String[] args)$
    }
    class GraphExporter {
        +main(String[] args)$
        +toDot(StoryGraph g) String
    }

    class GameException {
        <<abstract>>
    }
    class InvalidChoiceException
    class LockedChoiceException
    class InventoryFullException
    class SaveFailedException
    class ContentValidationException
    class ActionNotAllowedException
    class SaveNotFoundException

    GameListener <|.. AchievementManager
    EventBus o-- GameListener
    EventBus ..> GameEvent : publishes
    GameEvent --> EventType
    AchievementManager o-- AchievementRule
    AchievementManager --> MetaRepository
    ContentImporter ..> ContentRepository
    ContentImporter ..> StoryValidator
    ContentRepository ..> SqlLog
    SaveRepository ..> SqlLog
    SaveRepository ..> SaveData : load returns raw rows (D22)
    MetaRepository ..> SqlLog
    RuntimeException <|-- GameException
    GameException <|-- InvalidChoiceException
    GameException <|-- LockedChoiceException
    GameException <|-- InventoryFullException
    GameException <|-- SaveFailedException
    GameException <|-- ContentValidationException
    GameException <|-- ActionNotAllowedException
    GameException <|-- SaveNotFoundException

    class StoryValidator
    class RuntimeException
```

`ErrorHandler` maps `InvalidChoiceException` → 400, `LockedChoiceException` → 403, `SaveNotFoundException` → 404, `ActionNotAllowedException` → 409, `SaveFailedException` → 500 (D27). `ContentValidationException` only occurs at startup or import. `InventoryRow (itemId, qty, equipped)` and `HistoryRow (stepNo, nodeId, choiceId)` are small records nested in `SaveData`. `Location` is a record `(id, name, region)`.
