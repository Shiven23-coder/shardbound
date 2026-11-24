package com.shardbound.engine;

import java.util.HashSet;
import com.shardbound.world.Chest;
import com.shardbound.world.Lantern;
import com.shardbound.view.LevelPreviewView;
import com.shardbound.progression.Attributes;
import com.shardbound.progression.PlayerStats;
import com.shardbound.progression.ProgressionRules;
import com.shardbound.progression.LevelUp;
import com.shardbound.progression.Attribute;
import com.shardbound.achievement.AchievementManager;
import com.shardbound.battle.BattleAction;
import com.shardbound.battle.BattleEngine;
import com.shardbound.battle.BattleOutcome;
import com.shardbound.battle.BattleState;
import com.shardbound.battle.BossTemplate;
import com.shardbound.battle.CreepTemplate;
import com.shardbound.battle.Encounter;
import com.shardbound.battle.Skill;
import com.shardbound.exception.IdempotencyKeyReusedException;
import com.shardbound.world.CreepSpawn;
import com.shardbound.world.WorldRules;
import com.shardbound.content.GameContent;
import com.shardbound.cutscene.Cutscene;
import com.shardbound.dialogue.Choice;
import com.shardbound.dialogue.Dialogue;
import com.shardbound.dialogue.DialogueGraph;
import com.shardbound.dialogue.DialogueNode;
import com.shardbound.engine.port.MetaStore;
import com.shardbound.engine.port.RunStore;
import com.shardbound.engine.port.SaveStore;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.InvalidChoiceException;
import com.shardbound.exception.LockedChoiceException;
import com.shardbound.exception.SaveNotFoundException;
import com.shardbound.exception.WorldEventRejectedException;
import com.shardbound.item.Consumable;
import com.shardbound.item.Flask;
import com.shardbound.item.Inventory;
import com.shardbound.item.Item;
import com.shardbound.item.Weapon;
import com.shardbound.model.Faction;
import com.shardbound.model.Stats;
import com.shardbound.rules.Rewards;
import com.shardbound.save.SaveSnapshot;
import com.shardbound.save.SlotSummary;
import com.shardbound.view.SessionView;
import com.shardbound.view.MetaViews.InspectorView;
import com.shardbound.view.MetaViews.QuestStatusView;
import com.shardbound.view.WeaponView;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import com.shardbound.world.AreaMap;
import com.shardbound.world.Facing;
import com.shardbound.world.MapObject;
import com.shardbound.world.ObjectType;
import com.shardbound.world.World;
import com.shardbound.world.WorldEventValidator;
import com.shardbound.world.WorldPosition;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Comparator;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game's rules in motion: world events, encounters, dialogue choices,
 * battle routing, equipment, saving and loading. The web layer calls ONLY
 * this class for gameplay (D21, D22).
 *
 * <p>Idempotency (P-56): the web layer wraps every state-changing call in
 * {@link #once}, so a repeated request applies nothing and the key is
 * remembered in the same run write as the action.
 *
 * <p>Thread safety: one GameEngine serves every session. It holds only
 * immutable content and thread-safe collaborators; every public method that
 * touches a GameState runs inside {@code synchronized (state)} (D16).
 *
 * <p>Durability (P-22): after every state change the engine writes the run
 * through to {@link RunStore} before it builds the response, so a restart or
 * deploy never loses progress. A write that loses an optimistic-locking race
 * throws {@link com.shardbound.exception.RunConflictException}.
 *
 * <p>The three stores may be null for in-memory use (unit tests, tools):
 * saving is then refused and nothing outlives the process.
 */
public class GameEngine {

    private static final Logger LOG = LoggerFactory.getLogger(GameEngine.class);

    public static final int AUTOSAVE_SLOT = 0;
    public static final int FIRST_MANUAL_SLOT = 1;
    public static final int LAST_MANUAL_SLOT = 3;

    private final GameContent content;
    private final DialogueGraph graph;
    private final World world;
    private final WorldEventValidator worldEvents;
    private final Clock clock;
    private final BattleEngine battleEngine;
    private final EndingResolver endingResolver;
    private final ViewBuilder views;
    private final GameSnapshots snapshots;
    private final Death death;
    private final LevelUp levelUp;
    private final @Nullable SaveStore saves;
    private final @Nullable MetaStore meta;
    private final @Nullable RunStore runs;
    private final ExecutorService autosaver;     // single thread: autosaves never overlap

    public GameEngine(GameContent content, @Nullable SaveStore saves, @Nullable MetaStore meta,
                      @Nullable RunStore runs) {
        this(content, saves, meta, runs, Clock.systemUTC());
    }

    /** @param clock server time for the world-event speed check (tests pass a fixed clock) */
    public GameEngine(GameContent content, @Nullable SaveStore saves, @Nullable MetaStore meta,
                      @Nullable RunStore runs, Clock clock) {
        this.content = content;
        this.graph = content.graph();
        this.world = content.world();
        this.worldEvents = new WorldEventValidator(content.worldRules());
        this.clock = clock;
        this.battleEngine = new BattleEngine(content.battle(), content.items());
        this.endingResolver = new EndingResolver(content.endings());
        this.views = new ViewBuilder(content);
        this.snapshots = new GameSnapshots(content, battleEngine);
        this.death = new Death(world);
        this.levelUp = new LevelUp(content.progression());
        this.saves = saves;
        this.meta = meta;
        this.runs = runs;
        this.autosaver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "autosave");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** An engine with no persistence at all (tests and tools). */
    public static GameEngine inMemory(GameContent content) {
        return new GameEngine(content, null, null, null);
    }

    public GameContent content() {
        return content;
    }

    /** A fresh random session id, also the client's X-Session token. */
    public static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    // =====================================================================
    // New game, resume and views
    // =====================================================================

    /**
     * Creates a game at the start of regions.json: the start area and spawn,
     * the start flags, the starting kit (P-55), the creeps' levels and the
     * opening dialogue. Stores it as a new run.
     */
    public GameState newGame(int playerId, boolean demoMode) {
        return newGame(playerId, demoMode, new Random().nextLong());
    }

    /** Same, with a chosen RNG seed (tests). */
    public GameState newGame(int playerId, boolean demoMode, long seed) {
        World.Start start = world.start();
        World.Kit kit = start.kit();
        Inventory inventory = new Inventory();
        inventory.add(content.items().get(kit.weapon()), 1);
        inventory.equip(kit.weapon());
        kit.bag().forEach((itemId, qty) -> inventory.add(content.items().get(itemId), qty));
        AreaMap map = world.mapOf(start.area());
        WorldPosition position = WorldPosition.at(start.area(), map, map.spawn(start.spawn()).orElseThrow());
        GameState s = new GameState(newSessionId(), playerId, null, position,
                Stats.starting(content.progression(), kit.gold()), inventory, seed, demoMode, 0);
        s.moveTo(position, clock.millis());
        kit.skills().forEach(s::learnSkill);
        s.refillFlasks();
        s.creeps().respawn(world, s.rng());
        content.questManager().initialStates(s);
        start.flags().forEach(s::setFlag);
        wireSession(s);
        openDialogue(s, start.dialogue());
        storeAsNewRun(s);
        return s;
    }

    /**
     * Rebuilds a game from its durable run after a restart or cache eviction.
     * Empty if there is no stored run for this session.
     */
    public Optional<GameState> resume(String sessionId) {
        if (runs == null) {
            return Optional.empty();
        }
        return runs.find(sessionId).map(stored -> {
            GameState s = snapshots.restoreRun(stored.snapshot(), sessionId, stored.playerId());
            s.setRunVersion(stored.version());
            s.moveTo(s.position(), clock.millis());
            wireSession(s);
            return s;
        });
    }

    /**
     * Applies a state-changing action once per idempotency key (P-56): a new
     * key runs {@code action}, which remembers the key in its run write; a
     * repeat with the same request returns the current view and applies
     * nothing; the same key with a different request is refused.
     */
    public SessionView once(GameState s, ActionKey key, Supplier<SessionView> action) {
        synchronized (s) {
            switch (s.recentActions().match(key)) {
                case REPEAT -> {
                    return views.build(s);
                }
                case REUSED -> throw new IdempotencyKeyReusedException();
                case NEW -> { }
            }
            s.setPendingKey(key);
            try {
                return action.get();
            } finally {
                s.setPendingKey(null);
            }
        }
    }

    /** The same for an action with no view to return (saving to a slot). */
    public void once(GameState s, ActionKey key, Runnable action) {
        once(s, key, () -> {
            action.run();
            return views.build(s);
        });
    }

    /** The current screen. */
    public SessionView view(GameState s) {
        synchronized (s) {
            return views.build(s);
        }
    }

    // =====================================================================
    // World events (§4.11)
    // =====================================================================

    /**
     * The player walked onto an exit: after checks 1–5 the player arrives at
     * the exit's spawn point in the next area, and the game autosaves.
     */
    public SessionView exit(GameState s, String exitId, int x, int y, Facing facing) {
        synchronized (s) {
            WorldPosition last = s.position();
            AreaMap map = requireWorld(s);
            int[] tile = worldEvents.checkPosition(map, WorldObjects.collision(s, last.areaId(), map), last,
                    clock.millis() - s.lastWorldEventMillis(), x, y);
            MapObject exit = map.object(exitId).filter(o -> o.type() == ObjectType.EXIT)
                    .orElseThrow(() -> new WorldEventRejectedException("There is no way out there.", last));
            worldEvents.checkOnExit(exit, tile, last);
            String toArea = exit.property("toArea");
            World.Edge edge = world.edgeBetween(last.areaId(), toArea).orElseThrow();
            AreaMap target = world.maps().get(toArea);
            if ((edge.gateFlag() != null && s.flag(edge.gateFlag()) == 0) || target == null) {
                throw new WorldEventRejectedException("The way is closed.", last);
            }
            MapObject spawn = target.spawn(exit.property("toSpawn")).orElseThrow();
            WorldPosition arrival = WorldPosition.at(toArea, target, spawn);
            s.moveTo(new WorldPosition(toArea, arrival.x(), arrival.y(), facing), clock.millis());
            autosave(s);
            return persistAndView(s);
        }
    }

    /**
     * The player acted on an NPC or object, or walked into a trigger: after
     * checks 1–5 its dialogue opens. A once-only trigger never fires again.
     */
    public SessionView interact(GameState s, String targetId, int x, int y, Facing facing) {
        synchronized (s) {
            WorldPosition last = s.position();
            AreaMap map = requireWorld(s);
            int[] tile = worldEvents.checkPosition(map, WorldObjects.collision(s, last.areaId(), map), last,
                    clock.millis() - s.lastWorldEventMillis(), x, y);
            MapObject target = map.object(targetId)
                    .filter(o -> o.type().interactive() && WorldObjects.active(s, last.areaId(), o))
                    .orElseThrow(() -> new WorldEventRejectedException("There is nothing to do there.", last));
            worldEvents.checkInRange(target, tile, last);
            if (target.type() == ObjectType.CHEST && s.flag(Chest.of(last.areaId(), target).flag()) != 0) {
                throw new WorldEventRejectedException("The chest is empty.", last);
            }
            s.moveTo(new WorldPosition(last.areaId(), x, y, facing), clock.millis());
            if (target.type() == ObjectType.TRIGGER && target.flag("once")) {
                s.setFlag(WorldObjects.onceFlag(last.areaId(), target), 1);
            }
            if (target.type() == ObjectType.CHEST) {
                Chests.open(s, Chest.of(last.areaId(), target), content);
                return persistAndView(s);
            }
            if (target.type() == ObjectType.TRIGGER && target.property("boss") != null) {
                return bossTrigger(s, content.battle().bosses().get(target.property("boss")));
            }
            if (target.type() == ObjectType.LANTERN) {
                Lantern lantern = Lantern.of(last.areaId(), target);
                if (s.flag(lantern.litFlag()) != 0) {
                    return rest(s, lantern);
                }
            }
            openDialogue(s, target.property("dialogue"));        // a cold Lantern: its cold or kindling message
            return persistAndView(s);
        }
    }

    // =====================================================================
    // Bosses and cutscenes (P-67, P-68)
    // =====================================================================

    /** The engine flag that records a cutscene as seen in this run (D15: content may read it, never set it). */
    public static String seenFlag(String cutsceneId) {
        return "_seen_" + cutsceneId;
    }

    /**
     * The player stepped onto a boss's trigger: its intro the first time, else straight into the fight (the
     * client shows the boss name banner), so the intro never replays after a death.
     */
    private SessionView bossTrigger(GameState s, BossTemplate boss) {
        if (s.flag(seenFlag(boss.introCutscene())) == 0) {
            playCutscene(s, boss.introCutscene());
        } else {
            startBossBattle(s, boss);
        }
        return persistAndView(s);
    }

    private static void playCutscene(GameState s, String cutsceneId) {
        s.setCutscene(cutsceneId);
        s.setMode(Mode.CUTSCENE);
    }

    private void startBossBattle(GameState s, BossTemplate boss) {
        s.setBattle(battleEngine.startBoss(s, boss));
        s.setMode(Mode.BATTLE);
    }

    /**
     * The client finished a cutscene, played out or skipped (P-68): it is recorded as seen, then its
     * {@code then} follows: a boss battle, or the world.
     */
    public SessionView finishCutscene(GameState s, String cutsceneId) {
        synchronized (s) {
            requireMode(s, Mode.CUTSCENE, "No cutscene is playing.");
            if (!cutsceneId.equals(s.cutscene())) {
                throw new ActionNotAllowedException("That cutscene is not the one playing.");
            }
            Cutscene cutscene = content.cutscenes().get(cutsceneId);
            s.setFlag(seenFlag(cutsceneId), 1);
            s.setCutscene(null);
            String boss = cutscene.thenBoss();
            if (boss != null) {
                startBossBattle(s, content.battle().bosses().get(boss));
            } else {
                s.setMode(Mode.WORLD);
                s.moveTo(s.position(), clock.millis());      // walking time counts from here
            }
            return persistAndView(s);
        }
    }

    /**
     * A boss battle ends (P-67): a victory records the boss's fall and plays its epilogue; a loss goes
     * through Death like any creep battle (Lost Shards on the battle tile, waking at the last Lantern).
     */
    private void endBossBattle(GameState s, BattleState battle, BossTemplate boss, BattleOutcome outcome) {
        switch (outcome) {
            case WON -> {
                s.setFlag(boss.defeatFlag(), 1);
                playCutscene(s, boss.epilogueCutscene());
            }
            case LOST -> {
                death.apply(s, clock.millis()).forEach(battle::close);
                s.setMode(Mode.WORLD);
                s.moveTo(s.position(), clock.millis());
            }
            case FLED, ONGOING -> throw new IllegalStateException("A boss battle cannot end " + outcome);
        }
    }

    /**
     * The player came within a creep's aggro radius (V2_SPEC §4.2): after
     * checks 1–4, the server picks the first living creep within its radius
     * of the validated position (ties to the lowest object name), skipping one
     * the player recently fled from (P-54); it must be the creep the client
     * named. The battle starts.
     */
    public SessionView encounter(GameState s, String creepId, int x, int y, Facing facing) {
        synchronized (s) {
            WorldPosition last = s.position();
            AreaMap map = requireWorld(s);
            long now = clock.millis();
            int[] tile = worldEvents.checkPosition(map, WorldObjects.collision(s, last.areaId(), map), last,
                    now - s.lastWorldEventMillis(), x, y);
            CreepSpawn creep = engagedCreep(s, map, last.areaId(), tile, now)
                    .filter(c -> c.name().equals(creepId))
                    .orElseThrow(() -> new WorldEventRejectedException("Nothing attacks you there.", last));
            s.moveTo(new WorldPosition(last.areaId(), x, y, facing), now);
            String key = CreepRoster.key(last.areaId(), creep.name());
            CreepTemplate template = content.battle().creeps().get(creep.template());
            s.setBattle(battleEngine.start(s, template, s.creeps().level(key), key, null, true));
            s.setMode(Mode.BATTLE);
            return persistAndView(s);
        }
    }

    /** The first living, attentive creep within its aggro radius of {@code tile}, by object name. */
    private Optional<CreepSpawn> engagedCreep(GameState s, AreaMap map, String areaId, int[] tile, long now) {
        long ignoreMillis = fleeIgnoreMillis();
        return map.objectsOf(ObjectType.CREEP_SPAWN).stream()
                .filter(o -> WorldObjects.active(s, areaId, o))
                .map(CreepSpawn::of)
                .filter(c -> !s.creeps().isDefeated(CreepRoster.key(areaId, c.name())))
                .filter(c -> !s.creeps().ignores(CreepRoster.key(areaId, c.name()), now, ignoreMillis))
                .filter(c -> c.distanceTo(tile[0], tile[1]) <= c.aggroRadius())
                .min(Comparator.comparing(CreepSpawn::name));
    }

    /** How long a creep fled from ignores the player: time to walk {@code ignoreTiles} out and back (P-54). */
    private long fleeIgnoreMillis() {
        WorldRules rules = content.worldRules();
        return Math.round(2.0 * content.battle().rules().fleeIgnoreTiles() * rules.stepMs() / rules.speedTolerance());
    }

    /** Check 1: world events only in WORLD mode. Returns the current area's map. */
    private AreaMap requireWorld(GameState s) {
        if (s.mode() != Mode.WORLD) {
            throw new WorldEventRejectedException("You cannot walk around right now.", s.position());
        }
        return world.mapOf(s.position().areaId());
    }

    // =====================================================================
    // Dialogue (spec §6.3)
    // =====================================================================

    /** Opens a dialogue on its first node for this game. Called with the lock held. */
    private void openDialogue(GameState s, String dialogueId) {
        Dialogue dialogue = graph.dialogue(dialogueId);
        s.setMode(Mode.DIALOGUE);
        enterNode(s, dialogue.firstNode(s));
    }

    /**
     * Takes a choice: re-checks it exists and is unlocked (the client is never
     * trusted), applies its effects, records the step, then enters the target
     * or, for a choice that ends the dialogue, returns to the world.
     */
    public SessionView choose(GameState s, int choiceId) {
        synchronized (s) {
            requireMode(s, Mode.DIALOGUE, "Choices are not available right now.");
            EventBus bus = s.bus();
            Choice c = graph.choice(s.currentNode(), choiceId)
                    .orElseThrow(() -> new InvalidChoiceException(choiceId));
            if (!c.conditions().test(s)) {
                throw new LockedChoiceException(c.label(), c.conditions().describe());
            }
            s.setOfferReload(false);
            if (c.hasRepOrAffinityCondition()) {                    // D15: SILVER_TONGUE counter
                s.setFlag("_locked_passed", s.flag("_locked_passed") + 1);
                bus.publish(new GameEvent(EventType.LOCKED_CHOICE_PASSED, Map.of("choice", choiceId)));
            }
            for (var effect : c.effects()) {
                effect.apply(s, bus);
            }
            s.historyStack().push(new Step(s.currentNode(), choiceId));
            if (c.endsDialogue()) {
                s.setCurrentNode(null);
                s.setMode(Mode.WORLD);
                s.moveTo(s.position(), clock.millis());     // walking time counts from here
            } else {
                enterNode(s, c.to());
            }
            return persistAndView(s);
        }
    }

    /**
     * Moves to a node and handles its type. Called with the lock held.
     * Recursion depth ≤ 2 (RESOLVER → ENDING).
     */
    private void enterNode(GameState s, String nodeId) {
        DialogueNode n = graph.node(nodeId);
        s.setCurrentNode(nodeId);
        s.bus().publish(new GameEvent(EventType.NODE_ENTERED, Map.of("node", nodeId)));
        switch (n.type()) {
            case TEXT -> { }
            case COMBAT -> {
                Encounter encounter = content.encounters().get(n.encounterId());
                s.setBattle(battleEngine.start(s, content.battle().creeps().get(encounter.creepId()), encounter.level(),
                        null, encounter.id(), encounter.canFlee()));
                s.setMode(Mode.BATTLE);
            }
            case RESOLVER -> enterNode(s, endingResolver.resolve(s).nodeId());
            case ENDING -> finish(s, n);
        }
    }

    /**
     * Ending reached (spec §6.9): record it (keeping the best time), publish
     * ENDING_REACHED for SHORT_TALE, SPEEDRUNNER and ALL_ENDINGS, switch to ENDED.
     */
    private void finish(GameState s, DialogueNode node) {
        Ending ending = content.endingAtNode(node.id());
        String endingId = ending == null ? node.id() : ending.id();
        int seconds = s.playSeconds();
        int found = 1;
        if (meta != null) {
            meta.recordEnding(s.playerId(), endingId, seconds);
            found = meta.endingsFor(s.playerId()).size();
        }
        s.recordEnding(seconds, found);
        s.setMode(Mode.ENDED);
        Map<String, Object> data = new HashMap<>();
        data.put("endingId", endingId);
        data.put("seconds", seconds);
        data.put("endingsFound", found);
        data.put("endingsTotal", content.endings().size());
        s.bus().publish(new GameEvent(EventType.ENDING_REACHED, data));
    }

    // =====================================================================
    // Battles (V2_SPEC §4.7, D21, P-55)
    // =====================================================================

    /**
     * One battle round, then routing on the outcome. A creep's battle returns
     * to the world (won: the creep stays down; fled: it ignores the player for
     * a while; lost: the interim respawn). A dialogue fight goes to its win,
     * lose or flee node. The view of the last round is shown once.
     */
    public SessionView battleAction(GameState s, BattleAction action) {
        synchronized (s) {
            requireMode(s, Mode.BATTLE, "You are not in a battle.");
            checkBattleAction(s, action);
            BattleState battle = s.battle();
            BattleOutcome outcome = battleEngine.act(s, action);
            if (outcome != BattleOutcome.ONGOING) {
                s.setBattle(null);
                s.setFinishedBattle(battle);
                if (battle.enemy().boss() != null) {
                    endBossBattle(s, battle, battle.enemy().boss(), outcome);
                } else if (battle.creepRef() != null) {
                    endCreepBattle(s, battle, outcome);
                } else {
                    endDialogueBattle(s, battle.encounterId(), outcome);
                }
            }
            return persistAndView(s);
        }
    }

    /** Rejects actions the current battle does not allow (409, D27). */
    private void checkBattleAction(GameState s, BattleAction action) {
        switch (action.kind()) {
            case FLEE -> {
                if (!s.battle().canFlee()) {
                    throw new ActionNotAllowedException("There is no escape from this battle.");
                }
            }
            case SKILL -> {
                String skillId = action.slot() >= 0 && action.slot() < GameState.LOADOUT_SLOTS
                        ? s.loadout().get(action.slot()) : null;
                if (skillId == null) {
                    throw new ActionNotAllowedException("There is no skill in that slot.");
                }
                Skill skill = battleEngine.skill(skillId);
                if (s.stats().fp() < skill.fpCost()) {
                    throw new ActionNotAllowedException("Not enough FP for " + skill.name() + ".");
                }
            }
            case ITEM -> requireUsable(s, action.itemId());
            case STRIKE, GUARD -> { }
        }
    }

    private void endCreepBattle(GameState s, BattleState battle, BattleOutcome outcome) {
        String creepRef = battle.creepRef();
        switch (outcome) {
            case WON -> s.creeps().markDefeated(creepRef);
            case FLED -> s.creeps().markFled(creepRef, clock.millis());
            case LOST -> death.apply(s, clock.millis()).forEach(battle::close);   // read before Continue (P-62)
            case ONGOING -> throw new IllegalStateException("The battle is still on");
        }
        s.setMode(Mode.WORLD);
        s.moveTo(s.position(), clock.millis());          // walking time counts from here
    }

    /** A dialogue fight (P-37) goes on in its dialogue; a loss leaves the player at half HP (P-55). */
    private void endDialogueBattle(GameState s, String encounterId, BattleOutcome outcome) {
        DialogueNode node = graph.node(s.currentNode());
        String next = switch (outcome) {
            case WON -> node.winNode();
            case LOST -> {
                s.stats().setHp(s.stats().maxHp() * content.battle().rules().dialogueLossHpPct() / 100);
                yield node.loseNode();
            }
            case FLED -> node.fleeNode() != null ? node.fleeNode() : node.loseNode();
            case ONGOING -> throw new IllegalStateException("The battle " + encounterId + " is still on");
        };
        s.setMode(Mode.DIALOGUE);
        enterNode(s, next);
    }

    // =====================================================================
    // Lanterns and Lost Shards (V2_SPEC §4.1, §4.3, §4.4, P-61, P-62)
    // =====================================================================

    /**
     * Rest at a lit Lantern (P-61), one transaction: HP, FP and the Lantern
     * Draught full; the creeps respawn with new levels; the Lantern becomes the
     * respawn point; slot 0 is written with the run; the Lantern menu opens.
     */
    private SessionView rest(GameState s, Lantern lantern) {
        s.stats().restoreAll();
        s.refillFlasks();
        s.creeps().respawn(world, s.rng());
        s.setLastLantern(lantern.id());
        s.setMode(Mode.LANTERN);
        reseedAndPersist(s);
        if (saves != null) {
            saves.save(s.playerId(), AUTOSAVE_SLOT, snapshots.save(s), snapshots.areaOf(s));
        }
        return views.build(s);
    }

    /** What raising these attributes would do (P-61); nothing changes. */
    public LevelPreviewView previewLevelUp(GameState s, Map<Attribute, Integer> raises) {
        synchronized (s) {
            requireMode(s, Mode.LANTERN, "Rest at a Lantern to level up.");
            LevelUp.Plan plan = plan(s, raises);
            Weapon weapon = s.inventory().equippedWeapon();
            return new LevelPreviewView(plan.before().level(), plan.after().level(), plan.cost(),
                    s.stats().shards() - plan.cost(), plan.affordable(),
                    ViewBuilder.statsOf(new PlayerStats(plan.before(), content.progression()), weapon),
                    ViewBuilder.statsOf(new PlayerStats(plan.after(), content.progression()), weapon));
        }
    }

    /** Buys the levels of these raises in one go (P-61): 409 if the Shards do not cover them. */
    public SessionView levelUp(GameState s, Map<Attribute, Integer> raises) {
        synchronized (s) {
            requireMode(s, Mode.LANTERN, "Rest at a Lantern to level up.");
            LevelUp.Plan plan = plan(s, raises);
            if (plan.levels() == 0) {
                throw new ActionNotAllowedException("Choose at least one attribute to raise.");
            }
            if (!plan.affordable()) {
                throw new ActionNotAllowedException("You need " + plan.cost() + " Shards for that.");
            }
            s.stats().applyLevelUp(plan);
            s.bus().publish(GameEvent.of(EventType.LEVEL_UP, "Level " + plan.after().level(),
                    "level", String.valueOf(plan.after().level())));
            return persistAndView(s);
        }
    }

    private LevelUp.Plan plan(GameState s, Map<Attribute, Integer> raises) {
        try {
            return levelUp.plan(s.stats().attributes(), raises, s.stats().shards());
        } catch (IllegalArgumentException e) {
            throw new ActionNotAllowedException(e.getMessage());
        }
    }

    /** Sets the four skill slots at a Lantern (P-61): known skills, each at most once, or empty. */
    public SessionView setLoadout(GameState s, List<@Nullable String> slots) {
        synchronized (s) {
            requireMode(s, Mode.LANTERN, "Change your skills at a Lantern.");
            if (slots.size() != GameState.LOADOUT_SLOTS) {
                throw new ActionNotAllowedException("The loadout has " + GameState.LOADOUT_SLOTS + " slots.");
            }
            Set<String> placed = new HashSet<>();
            for (String skill : slots) {
                if (skill != null && (!s.knownSkills().contains(skill) || !placed.add(skill))) {
                    throw new ActionNotAllowedException("Each slot takes a skill you know, once.");
                }
            }
            s.restoreSkills(s.knownSkills(), slots);
            return persistAndView(s);
        }
    }

    /** Leaves the Lantern menu for the world. */
    public SessionView leaveLantern(GameState s) {
        synchronized (s) {
            requireMode(s, Mode.LANTERN, "You are not at a Lantern.");
            s.setMode(Mode.WORLD);
            s.moveTo(s.position(), clock.millis());          // walking time counts from here
            return persistAndView(s);
        }
    }

    /**
     * The player stepped within one tile of their Lost Shards (V2_SPEC §4.4, P-62): after checks
     * 1–4 the Shards come back.
     */
    public SessionView recover(GameState s, int x, int y, Facing facing) {
        synchronized (s) {
            WorldPosition last = s.position();
            AreaMap map = requireWorld(s);
            int[] tile = worldEvents.checkPosition(map, WorldObjects.collision(s, last.areaId(), map), last,
                    clock.millis() - s.lastWorldEventMillis(), x, y);
            LostShards lost = s.lostShards();
            if (lost == null || !lost.areaId().equals(last.areaId())
                    || Math.max(Math.abs(tile[0] - lost.tileX()), Math.abs(tile[1] - lost.tileY())) > 1) {
                throw new WorldEventRejectedException("There is nothing to pick up there.", last);
            }
            s.moveTo(new WorldPosition(last.areaId(), x, y, facing), clock.millis());
            s.setLostShards(null);
            Rewards.grantShards(s, s.bus(), lost.amount());
            s.addToast(new Toast("SHARDS", "Shards recovered"));
            return persistAndView(s);
        }
    }

    // =====================================================================
    // Inventory outside combat
    // =====================================================================

    public SessionView equip(GameState s, String itemId) {
        synchronized (s) {
            requireOutsideBattle(s, "You can only change weapons outside battle.");
            Weapon weapon = s.inventory().equip(itemId);
            if (weapon == null) {
                throw new ActionNotAllowedException("You can only equip a weapon you own.");
            }
            s.bus().publish(GameEvent.of(EventType.ITEM_EQUIPPED, "Equipped: " + weapon.name()
                    + " (" + weapon.tier() + ")", "itemId", weapon.id()));
            return persistAndView(s);
        }
    }

    public SessionView use(GameState s, String itemId) {
        synchronized (s) {
            requireOutsideBattle(s, "Use items from the BAG during a battle.");
            switch (requireUsable(s, itemId)) {
                case Flask flask -> Rewards.useFlask(s, s.bus(), flask);
                case Consumable consumable -> Rewards.useConsumable(s, s.bus(), consumable);
                default -> throw new IllegalStateException("Unusable item " + itemId);
            }
            return persistAndView(s);
        }
    }

    /** A consumable in the bag, or a flask with a charge left (409 otherwise, D27). */
    private Item requireUsable(GameState s, @Nullable String itemId) {
        Item item = itemId == null ? null : s.inventory().get(itemId);
        if (item instanceof Flask flask && s.flaskCharges(flask.id()) > 0 || item instanceof Consumable) {
            return item;
        }
        throw new ActionNotAllowedException(item instanceof Flask flask
                ? "The " + flask.name() + " is empty. Rest at a Lantern to refill it."
                : "You have no usable item like that.");
    }

    // =====================================================================
    // Save slots (spec §6.10, D7, D22, D24)
    // =====================================================================

    /**
     * Manual save to slot 1–3: WORLD mode only. The live run is reseeded and
     * written first, and the slot gets the same snapshot, so continuing and
     * loading this slot later roll the same dice (D7).
     */
    public void save(GameState s, int slot) {
        synchronized (s) {
            if (saves == null) {
                throw new ActionNotAllowedException("Saving is not available on this server.");
            }
            if (slot < FIRST_MANUAL_SLOT || slot > LAST_MANUAL_SLOT) {
                throw new ActionNotAllowedException("Choose slot 1, 2 or 3 (slot 0 is the autosave).");
            }
            if (s.mode() != Mode.WORLD) {
                throw new ActionNotAllowedException("You can only save while walking the world.");
            }
            reseedAndPersist(s);
            saves.save(s.playerId(), slot, snapshots.save(s), snapshots.areaOf(s));
        }
    }

    /**
     * Autosave to slot 0 (D17, D24), called with the lock held. The snapshot is
     * an immutable record, so the background thread can write it without the
     * lock. A failure is logged and never interrupts play.
     */
    private void autosave(GameState s) {
        if (saves == null) {
            return;
        }
        s.reseedForSave();
        SaveSnapshot snapshot = snapshots.save(s);
        String area = snapshots.areaOf(s);
        int playerId = s.playerId();
        SaveStore store = saves;
        autosaver.submit(() -> {
            try {
                store.save(playerId, AUTOSAVE_SLOT, snapshot, area);
            } catch (RuntimeException e) {
                LOG.warn("Autosave failed for player {}: {}", playerId, e.getMessage());
            }
        });
    }

    /**
     * Loads a slot into a NEW GameState (D22) and makes it the session's run.
     * No effects are re-applied.
     *
     * @param sessionId reuse this session (the caller's) or null for a new one
     */
    public GameState load(int playerId, int slot, @Nullable String sessionId) {
        if (saves == null) {
            throw new ActionNotAllowedException("Loading is not available on this server.");
        }
        SaveSnapshot snapshot = saves.load(playerId, slot)
                .orElseThrow(() -> new SaveNotFoundException(playerId, slot));
        String id = sessionId != null ? sessionId : newSessionId();
        GameState s = snapshots.restoreSave(snapshot, id, playerId);
        wireSession(s);
        storeAsNewRun(s);
        return s;
    }

    public List<SlotSummary> listSaves(int playerId) {
        if (saves == null) {
            return List.of();
        }
        return saves.list(playerId);
    }

    /** Deletes a slot; an empty slot stays empty, so a retried request changes nothing (P-56). */
    public void deleteSave(int playerId, int slot) {
        if (saves == null) {
            throw new ActionNotAllowedException("Saves are not available on this server.");
        }
        saves.delete(playerId, slot);
    }

    // =====================================================================
    // Rehearsals (e2e profile only)
    // =====================================================================

    /**
     * Puts the run at a story point in one step (P-67): the rehearsal's flags, quests, standing, gear, skills and
     * raises on top of what the run has, rested, standing at its spawn. Only the {@code e2e} profile calls it.
     */
    public SessionView rehearse(GameState s, Rehearsal r) {
        synchronized (s) {
            AreaMap map = world.mapOf(r.area());
            MapObject spawn = map.spawn(r.spawn())
                    .orElseThrow(() -> new IllegalArgumentException("No spawn " + r.spawn() + " in " + r.area()));
            s.setBattle(null);
            s.setCutscene(null);
            s.setCurrentNode(null);
            s.setMode(Mode.WORLD);
            r.flags().forEach(s::setFlag);
            r.quests().forEach(s::setQuestStatus);
            r.reputation().forEach(s::addReputation);
            r.affinity().forEach(s::addAffinity);
            r.items().forEach(itemId -> s.inventory().add(item(itemId), 1));
            if (!s.inventory().has(r.weapon())) {
                s.inventory().add(item(r.weapon()), 1);
            }
            s.inventory().equip(r.weapon());
            r.skills().forEach(s::learnSkill);
            for (Attribute raise : r.raises()) {
                LevelUp.Plan plan = levelUp.plan(s.stats().attributes(), Map.of(raise, 1), Integer.MAX_VALUE);
                s.stats().addShards(plan.cost());
                s.stats().applyLevelUp(plan);
            }
            s.setLastLantern(r.lantern());
            s.stats().restoreAll();
            s.refillFlasks();
            s.creeps().respawn(world, s.rng());
            s.moveTo(WorldPosition.at(r.area(), map, spawn), clock.millis());
            return persistAndView(s);
        }
    }

    private Item item(String itemId) {
        Item item = content.items().get(itemId);
        if (item == null) {
            throw new IllegalArgumentException("No item " + itemId);
        }
        return item;
    }

    // =====================================================================
    // Inspector (dev profile only)
    // =====================================================================

    /** Every weapon in the content, best first, rated for a new player (P-65): the dev tier gallery shows them. */
    public List<WeaponView> weaponLadder() {
        ProgressionRules rules = content.progression();
        PlayerStats fresh = new PlayerStats(Attributes.starting(rules.attributeStart()), rules);
        return content.items().values().stream()
                .filter(Weapon.class::isInstance).map(Weapon.class::cast)
                .sorted(Inventory.BY_TIER_THEN_ATTACK)
                .map(weapon -> ViewBuilder.weaponView(fresh, weapon))
                .toList();
    }

    public InspectorView inspect(GameState s) {
        synchronized (s) {
            Map<String, Integer> reputation = new LinkedHashMap<>();
            for (Map.Entry<Faction, Integer> e : s.reputationView().entrySet()) {
                reputation.put(e.getKey().name(), e.getValue());
            }
            List<QuestStatusView> quests = new ArrayList<>();
            for (String questId : content.questManager().logOrder()) {
                quests.add(new QuestStatusView(questId, s.questStatus(questId).name()));
            }
            return new InspectorView(s.currentNode(), s.mode().name(), s.rngSeed(), s.runVersion(),
                    new HashMap<>(s.flagsView()), reputation, new HashMap<>(s.affinityView()), quests);
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    /** Writes the run through (P-22), then builds the response. Called with the lock held. */
    private SessionView persistAndView(GameState s) {
        reseedAndPersist(s);
        return views.build(s);
    }

    /**
     * Continues the dice into a fresh seed (D7) and stores the run with it, so
     * a reloaded run rolls exactly what this one would have rolled.
     */
    private void reseedAndPersist(GameState s) {
        s.reseedForSave();
        rememberPendingKey(s, s.runVersion() + 1);
        if (runs != null) {
            s.setRunVersion(runs.update(s.sessionId(), s.runVersion(), snapshots.run(s)));
        }
    }

    private void storeAsNewRun(GameState s) {
        rememberPendingKey(s, 1);
        if (runs != null) {
            s.setRunVersion(runs.replace(s.sessionId(), s.playerId(), snapshots.run(s)));
        }
    }

    /** The action being applied is remembered in the write that applies it (P-56). */
    private static void rememberPendingKey(GameState s, long version) {
        ActionKey key = s.pendingKey();
        if (key != null) {
            s.recentActions().remember(key, version);
            s.setPendingKey(null);
        }
    }

    /** One EventBus per session: toast listener + this player's AchievementManager (D23). */
    private void wireSession(GameState s) {
        EventBus bus = new EventBus(s);
        bus.subscribe((event, state) -> {
            Toast toast = Toast.from(event);
            if (toast != null) {
                state.addToast(toast);
            }
        });
        Set<String> unlocked = meta == null ? Set.of() : meta.unlockedFor(s.playerId());
        bus.subscribe(new AchievementManager(s.playerId(), unlocked, content.achievements(), meta));
        s.setBus(bus);
    }

    private static void requireMode(GameState s, Mode mode, String message) {
        if (s.mode() != mode) {
            throw new ActionNotAllowedException(message);
        }
    }

    private static void requireOutsideBattle(GameState s, String message) {
        if (s.mode() != Mode.WORLD && s.mode() != Mode.DIALOGUE && s.mode() != Mode.LANTERN) {
            throw new ActionNotAllowedException(message);
        }
    }

    /** Stops the autosave thread after queued saves finish. */
    public void shutdown() {
        autosaver.shutdown();
    }
}
