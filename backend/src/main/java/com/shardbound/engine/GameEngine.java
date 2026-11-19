package com.shardbound.engine;

import com.shardbound.achievement.AchievementManager;
import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.combat.CombatEngine;
import com.shardbound.combat.CombatOutcome;
import com.shardbound.combat.CombatState;
import com.shardbound.content.GameContent;
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
import com.shardbound.item.Consumable;
import com.shardbound.item.Inventory;
import com.shardbound.item.Item;
import com.shardbound.item.Weapon;
import com.shardbound.model.Faction;
import com.shardbound.model.Stats;
import com.shardbound.rules.Rewards;
import com.shardbound.save.SaveSnapshot;
import com.shardbound.save.SlotSummary;
import com.shardbound.story.Choice;
import com.shardbound.story.StoryGraph;
import com.shardbound.story.StoryNode;
import com.shardbound.view.GameView;
import com.shardbound.view.MetaViews.InspectorView;
import com.shardbound.view.MetaViews.QuestStatusView;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game's rules in motion: choices, combat routing, equipment, saving and
 * loading. The web layer calls ONLY this class for gameplay (D21, D22).
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

    public static final String STARTING_WEAPON = "rusty_shortsword";
    public static final int AUTOSAVE_SLOT = 0;
    public static final int FIRST_MANUAL_SLOT = 1;
    public static final int LAST_MANUAL_SLOT = 3;

    private final GameContent content;
    private final StoryGraph graph;
    private final CombatEngine combatEngine;
    private final EndingResolver endingResolver;
    private final ViewBuilder views;
    private final GameSnapshots snapshots;
    private final @Nullable SaveStore saves;
    private final @Nullable MetaStore meta;
    private final @Nullable RunStore runs;
    private final ExecutorService autosaver;     // single thread: autosaves never overlap

    public GameEngine(GameContent content, @Nullable SaveStore saves, @Nullable MetaStore meta,
                      @Nullable RunStore runs) {
        this.content = content;
        this.graph = content.graph();
        this.combatEngine = new CombatEngine(content.enemies(), content.encounters());
        this.endingResolver = new EndingResolver(content.endings());
        this.views = new ViewBuilder(content);
        this.snapshots = new GameSnapshots(content, combatEngine);
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

    /** Creates a game at p01 with the starting kit (spec §6.1) and stores it as a new run. */
    public GameState newGame(int playerId, boolean demoMode) {
        return newGame(playerId, demoMode, new Random().nextLong());
    }

    /** Same, with a chosen RNG seed (tests). */
    public GameState newGame(int playerId, boolean demoMode, long seed) {
        Inventory inventory = new Inventory();
        inventory.add(content.items().get(STARTING_WEAPON), 1);
        inventory.equip(STARTING_WEAPON);
        GameState s = new GameState(newSessionId(), playerId, StoryGraph.START_NODE,
                Stats.starting(), inventory, seed, demoMode, 0);
        content.questManager().initialStates(s);
        wireSession(s);
        s.bus().publish(new GameEvent(EventType.NODE_ENTERED, Map.of("node", StoryGraph.START_NODE)));
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
            wireSession(s);
            return s;
        });
    }

    /** The current screen. */
    public GameView view(GameState s) {
        synchronized (s) {
            return views.build(s);
        }
    }

    // =====================================================================
    // Story (spec §6.3)
    // =====================================================================

    /**
     * Takes a choice: re-checks it exists and is unlocked (the client is never
     * trusted), applies its effects, records the step and enters the target.
     */
    public GameView choose(GameState s, int choiceId) {
        synchronized (s) {
            requireMode(s, Mode.STORY, "Choices are not available right now.");
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
            enterNode(s, c.to());
            return persistAndView(s);
        }
    }

    /**
     * Moves to a node and handles its type. Called with the lock held.
     * Autosave runs BEFORE the type switch, so the snapshot is always taken
     * in STORY mode (D17). Recursion depth ≤ 2 (RESOLVER → ENDING).
     */
    private void enterNode(GameState s, String nodeId) {
        StoryNode previous = graph.node(s.currentNode());
        StoryNode n = graph.node(nodeId);
        s.setCurrentNode(nodeId);
        s.bus().publish(new GameEvent(EventType.NODE_ENTERED, Map.of("node", nodeId)));
        if (previous != null && n.chapter() != previous.chapter()) {
            autosave(s);
        }
        switch (n.type()) {
            case STORY -> { }
            case COMBAT -> {
                s.setCombat(combatEngine.start(s, n.encounterId()));
                s.setMode(Mode.COMBAT);
            }
            case RESOLVER -> enterNode(s, endingResolver.resolve(s).nodeId());
            case ENDING -> finish(s, n);
        }
    }

    /**
     * Ending reached (spec §6.9): record it (keeping the best time), publish
     * ENDING_REACHED for SHORT_TALE, SPEEDRUNNER and ALL_ENDINGS, switch to ENDED.
     */
    private void finish(GameState s, StoryNode node) {
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
    // Combat (spec §6.6, D21)
    // =====================================================================

    /**
     * One combat round, then routing on the outcome: WON → win_node,
     * LOST → lose_node, FLED → flee_node or lose_node.
     */
    public GameView combatAction(GameState s, Action action) {
        synchronized (s) {
            requireMode(s, Mode.COMBAT, "You are not in a fight.");
            checkCombatAction(s, action);
            StoryNode node = graph.node(s.currentNode());
            boolean boss = s.combat().hasBoss();
            CombatOutcome outcome = combatEngine.act(s, action);
            switch (outcome) {
                case ONGOING -> { }
                case WON -> leaveCombat(s, node.winNode(), false);
                case LOST -> leaveCombat(s, node.loseNode(), boss);
                case FLED -> leaveCombat(s, node.fleeNode() != null ? node.fleeNode() : node.loseNode(), false);
            }
            return persistAndView(s);
        }
    }

    /** Rejects actions the current fight does not allow (409, D27). */
    private void checkCombatAction(GameState s, Action action) {
        CombatState combat = s.combat();
        if (action.type() == ActionType.FLEE && !combat.canFlee()) {
            throw new ActionNotAllowedException("There is no escape from this fight.");
        }
        if (action.type() == ActionType.SKILL && s.stats().energy() < CombatEngine.SKILL_COST) {
            throw new ActionNotAllowedException("Not enough energy for Crystal Pulse.");
        }
        if (action.type() == ActionType.ITEM && !(s.inventory().get(action.itemId()) instanceof Consumable)) {
            throw new ActionNotAllowedException("You have no usable item like that.");
        }
    }

    private void leaveCombat(GameState s, String nextNode, boolean bossLoss) {
        s.setCombat(null);
        s.setMode(Mode.STORY);
        s.setOfferReload(bossLoss);
        enterNode(s, nextNode);
    }

    // =====================================================================
    // Inventory outside combat
    // =====================================================================

    public GameView equip(GameState s, String itemId) {
        synchronized (s) {
            requireMode(s, Mode.STORY, "You can only change weapons outside combat.");
            Weapon weapon = s.inventory().equip(itemId);
            if (weapon == null) {
                throw new ActionNotAllowedException("You can only equip a weapon you own.");
            }
            s.bus().publish(GameEvent.of(EventType.ITEM_EQUIPPED, "Equipped: " + weapon.name()
                    + " (" + weapon.tier() + ")", "itemId", weapon.id()));
            return persistAndView(s);
        }
    }

    public GameView use(GameState s, String itemId) {
        synchronized (s) {
            requireMode(s, Mode.STORY, "Use items from the combat menu during a fight.");
            Item item = s.inventory().get(itemId);
            if (!(item instanceof Consumable consumable)) {
                throw new ActionNotAllowedException("That item cannot be used.");
            }
            Rewards.useConsumable(s, s.bus(), consumable);
            return persistAndView(s);
        }
    }

    // =====================================================================
    // Save slots (spec §6.10, D7, D22, D24)
    // =====================================================================

    /**
     * Manual save to slot 1–3: STORY mode only. The live run is reseeded and
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
            if (s.mode() != Mode.STORY) {
                throw new ActionNotAllowedException("You cannot save during combat or after the ending.");
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

    public void deleteSave(int playerId, int slot) {
        if (saves == null) {
            throw new ActionNotAllowedException("Saves are not available on this server.");
        }
        if (!saves.delete(playerId, slot)) {
            throw new SaveNotFoundException(playerId, slot);
        }
    }

    // =====================================================================
    // Inspector (dev profile only)
    // =====================================================================

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
    private GameView persistAndView(GameState s) {
        reseedAndPersist(s);
        return views.build(s);
    }

    /**
     * Continues the dice into a fresh seed (D7) and stores the run with it, so
     * a reloaded run rolls exactly what this one would have rolled.
     */
    private void reseedAndPersist(GameState s) {
        s.reseedForSave();
        if (runs != null) {
            s.setRunVersion(runs.update(s.sessionId(), s.runVersion(), snapshots.run(s)));
        }
    }

    private void storeAsNewRun(GameState s) {
        if (runs != null) {
            s.setRunVersion(runs.replace(s.sessionId(), s.playerId(), snapshots.run(s)));
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

    /** Stops the autosave thread after queued saves finish. */
    public void shutdown() {
        autosaver.shutdown();
    }
}
