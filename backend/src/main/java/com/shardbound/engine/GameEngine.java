package com.shardbound.engine;

import com.shardbound.achievement.AchievementManager;
import com.shardbound.api.dto.GameView;
import com.shardbound.api.dto.MetaViews.InspectorView;
import com.shardbound.api.dto.MetaViews.QuestStatusView;
import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.combat.CombatEngine;
import com.shardbound.combat.CombatOutcome;
import com.shardbound.combat.CombatState;
import com.shardbound.content.GameContent;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.InvalidChoiceException;
import com.shardbound.exception.LockedChoiceException;
import com.shardbound.item.Consumable;
import com.shardbound.item.Inventory;
import com.shardbound.item.Item;
import com.shardbound.item.Weapon;
import com.shardbound.model.Faction;
import com.shardbound.model.Stats;
import com.shardbound.persistence.MetaRepository;
import com.shardbound.persistence.SaveData;
import com.shardbound.persistence.SaveRepository;
import com.shardbound.persistence.SqlLog;
import com.shardbound.quest.QuestStatus;
import com.shardbound.rules.Rewards;
import com.shardbound.story.Choice;
import com.shardbound.story.StoryGraph;
import com.shardbound.story.StoryNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The game's rules in motion: choices, combat routing, equipment, saving and
 * loading. Controllers call ONLY this class for gameplay (D21, D22).
 *
 * <p>Thread safety: one GameEngine serves every session. It holds only
 * immutable content and thread-safe collaborators; every public method that
 * touches a GameState runs inside {@code synchronized (state)} (D16), so two
 * requests for the same session run one after the other.
 *
 * <p>{@code saves} and {@code meta} may be null in offline console mode
 * (content loaded from JSON, no database): saving is then refused and
 * achievements/endings are not persisted.
 */
public class GameEngine {

    private static final Logger LOG = LoggerFactory.getLogger(GameEngine.class);

    public static final String STARTING_WEAPON = "rusty_shortsword";

    private final GameContent content;
    private final StoryGraph graph;
    private final CombatEngine combatEngine;
    private final EndingResolver endingResolver;
    private final ViewBuilder views;
    private final SaveRepository saves;          // null offline
    private final MetaRepository meta;           // null offline
    private final ExecutorService autosaver;     // single thread: autosaves never overlap

    public GameEngine(GameContent content, SaveRepository saves, MetaRepository meta) {
        this.content = content;
        this.graph = content.graph();
        this.combatEngine = new CombatEngine(content.enemies(), content.encounters());
        this.endingResolver = new EndingResolver(content.endings());
        this.views = new ViewBuilder(content);
        this.saves = saves;
        this.meta = meta;
        this.autosaver = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "autosave");
            thread.setDaemon(true);
            return thread;
        });
    }

    public GameContent content() {
        return content;
    }

    // =====================================================================
    // New game and views
    // =====================================================================

    /** Creates a session at p01 with the starting kit (spec §6.1). O(q) for q quests. */
    public GameState newGame(int playerId, boolean demoMode) {
        return newGame(playerId, demoMode, new Random().nextLong());
    }

    /** Same, with a chosen RNG seed (tests and ConsoleRunner --auto-quick). */
    public GameState newGame(int playerId, boolean demoMode, long seed) {
        Inventory inventory = new Inventory();
        inventory.add(content.items().get(STARTING_WEAPON), 1);
        inventory.equip(STARTING_WEAPON);
        GameState s = new GameState(SessionStore.newSessionId(), playerId, StoryGraph.START_NODE,
                Stats.starting(), inventory, seed, demoMode, 0);
        content.questManager().initialStates(s);
        wireSession(s);
        s.bus().publish(new GameEvent(EventType.NODE_ENTERED, Map.of("node", StoryGraph.START_NODE)));
        return s;
    }

    /** The current screen. O(n) in the state's size. */
    public GameView view(GameState s) {
        synchronized (s) {
            return views.build(s);
        }
    }

    // =====================================================================
    // Story (spec §6.3)
    // =====================================================================

    /**
     * Takes a choice: re-checks it exists and is unlocked (React is never
     * trusted), applies its effects, records the step and enters the target.
     * Time O(k + c + e) for k choices, c conditions, e effects, plus the view.
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
            return enterNode(s, c.to());
        }
    }

    /**
     * Moves to a node and handles its type. Called with the lock held.
     * Autosave runs BEFORE the type switch, so the snapshot is always taken
     * in STORY mode (D17). Recursion depth ≤ 2 (RESOLVER → ENDING).
     */
    private GameView enterNode(GameState s, String nodeId) {
        StoryNode previous = graph.node(s.currentNode());
        StoryNode n = graph.node(nodeId);
        s.setCurrentNode(nodeId);
        s.bus().publish(new GameEvent(EventType.NODE_ENTERED, Map.of("node", nodeId)));
        if (previous != null && n.chapter() != previous.chapter()) {
            autosave(s);
        }
        return switch (n.type()) {
            case STORY -> views.build(s);
            case COMBAT -> {
                s.setCombat(combatEngine.start(s, n.encounterId()));
                s.setMode(Mode.COMBAT);
                yield views.build(s);
            }
            case RESOLVER -> enterNode(s, endingResolver.resolve(s).nodeId());
            case ENDING -> finish(s, n);
        };
    }

    /**
     * Ending reached (spec §6.9): record it (keeping the best time), publish
     * ENDING_REACHED for SHORT_TALE / SPEEDRUNNER, switch to ENDED.
     */
    private GameView finish(GameState s, StoryNode node) {
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
        s.bus().publish(new GameEvent(EventType.ENDING_REACHED, data));
        return views.build(s);
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
            return switch (outcome) {
                case ONGOING -> views.build(s);
                case WON -> leaveCombat(s, node.winNode(), false);
                case LOST -> leaveCombat(s, node.loseNode(), boss);
                case FLED -> leaveCombat(s, node.fleeNode() != null ? node.fleeNode() : node.loseNode(), false);
            };
        }
    }

    /** Rejects actions the current fight does not allow (409, D27). O(1). */
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

    private GameView leaveCombat(GameState s, String nextNode, boolean bossLoss) {
        s.setCombat(null);
        s.setMode(Mode.STORY);
        s.setOfferReload(bossLoss);
        return enterNode(s, nextNode);
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
            return views.build(s);
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
            return views.build(s);
        }
    }

    // =====================================================================
    // Saving and loading (spec §6.10, D7, D22, D24)
    // =====================================================================

    /** Manual save to slot 1–3: lock, STORY-only, reseed the live state, write. */
    public void save(GameState s, int slot) {
        synchronized (s) {
            if (saves == null) {
                throw new ActionNotAllowedException("Saving needs the database (offline mode).");
            }
            if (slot < 1 || slot > 3) {
                throw new ActionNotAllowedException("Choose slot 1, 2 or 3 (slot 0 is the autosave).");
            }
            if (s.mode() != Mode.STORY) {
                throw new ActionNotAllowedException("You cannot save during combat or after the ending.");
            }
            s.reseedForSave();                                  // D7, on the live state
            saves.save(s, s.playerId(), slot);                  // student-implemented transaction
        }
    }

    /**
     * Autosave (D24), called with the lock held: reseed the LIVE state, copy
     * it (without bus, combat, toasts), then let the single background thread
     * write the copy. A failure is logged and never interrupts play.
     */
    private void autosave(GameState s) {
        if (saves == null) {
            return;
        }
        s.reseedForSave();
        GameState snapshot = s.deepCopy();
        autosaver.submit(() -> {
            try {
                saves.save(snapshot, snapshot.playerId(), 0);
            } catch (RuntimeException e) {
                LOG.warn("Autosave failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Loads a slot into a NEW GameState (D22): raw rows → objects via the
     * in-memory catalog, fresh EventBus + listeners, no effects re-applied.
     * The caller stores it in SessionStore. Time O(n) rows.
     *
     * @param sessionId reuse this id (the caller's session) or null for a new one
     */
    public GameState load(int playerId, int slot, String sessionId) {
        if (saves == null) {
            throw new ActionNotAllowedException("Loading needs the database (offline mode).");
        }
        SaveData data = saves.load(playerId, slot);
        String id = sessionId != null ? sessionId : SessionStore.newSessionId();
        GameState s = rebuild(data, id);
        wireSession(s);
        return s;
    }

    public List<SaveData.SlotSummary> listSaves(int playerId) {
        if (saves == null) {
            return List.of();
        }
        return saves.listSlots(playerId);
    }

    public void deleteSave(int playerId, int slot) {
        if (saves == null) {
            throw new ActionNotAllowedException("Saves need the database (offline mode).");
        }
        saves.delete(playerId, slot);
    }

    /** SaveData (ids) → GameState (objects). Unknown ids from old content are skipped. O(n). */
    private GameState rebuild(SaveData d, String sessionId) {
        Stats stats = new Stats(d.hp(), d.energy(), d.level(), d.xp(), d.gold());   // the rest is derived (D6, D28)
        Inventory inventory = new Inventory();
        String equipped = null;
        for (SaveData.InventoryRow row : d.inventory()) {
            Item item = content.items().get(row.itemId());
            if (item != null) {
                inventory.add(item, row.qty());
                if (row.equipped()) {
                    equipped = row.itemId();
                }
            }
        }
        if (equipped != null) {
            inventory.equip(equipped);
        }
        GameState s = new GameState(sessionId, d.playerId(), d.currentNode(), stats, inventory, d.rngSeed(),
                d.demoMode(), d.playSeconds());
        content.questManager().initialStates(s);                // quests added to content since the save
        for (Map.Entry<String, String> quest : d.quests().entrySet()) {
            s.setQuestStatus(quest.getKey(), QuestStatus.valueOf(quest.getValue()));
        }
        for (Map.Entry<String, Integer> flag : d.flags().entrySet()) {
            s.setFlag(flag.getKey(), flag.getValue());
        }
        for (Map.Entry<String, Integer> rep : d.reputation().entrySet()) {
            s.putReputation(Faction.valueOf(rep.getKey()), rep.getValue());
        }
        for (Map.Entry<String, Integer> aff : d.affinity().entrySet()) {
            s.putAffinity(aff.getKey(), aff.getValue());
        }
        for (SaveData.HistoryRow row : d.history()) {          // oldest first → newest ends on top
            s.historyStack().push(new Step(row.nodeId(), row.choiceId()));
        }
        return s;
    }

    // =====================================================================
    // Inspector (spec §9.1)
    // =====================================================================

    public InspectorView inspect(GameState s) {
        synchronized (s) {
            Map<String, Integer> reputation = new LinkedHashMap<>();
            for (Map.Entry<Faction, Integer> e : s.reputationView().entrySet()) {
                reputation.put(e.getKey().name(), e.getValue());
            }
            List<QuestStatusView> quests = new java.util.ArrayList<>();
            for (String questId : content.questManager().logOrder()) {
                quests.add(new QuestStatusView(questId, s.questStatus(questId).name()));
            }
            return new InspectorView(s.currentNode(), s.mode().name(), s.rngSeed(), new HashMap<>(s.flagsView()),
                    reputation, new HashMap<>(s.affinityView()), quests, SqlLog.lastFive());
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

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
