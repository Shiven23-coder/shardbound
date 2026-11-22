package com.shardbound.engine;

import com.shardbound.battle.BattleState;
import com.shardbound.event.EventBus;
import com.shardbound.item.Inventory;
import com.shardbound.model.Faction;
import com.shardbound.model.Stats;
import com.shardbound.quest.QuestStatus;
import com.shardbound.world.WorldPosition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

/**
 * Everything about one player's game in progress (one per session).
 *
 * <p>Encapsulation (D30): all fields are private. Other packages change the
 * state only through intent methods that keep the invariants (e.g. reputation
 * is always clamped to −100…100) and read collections through unmodifiable
 * views. Raw mutable collections are package-private for {@code GameEngine}.
 *
 * <p>Not thread-safe by itself: every public GameEngine method holds
 * {@code synchronized (state)} while it uses the state (D16).
 *
 * <p>A GameState is a cache of the durable run in {@code active_run} (P-22);
 * {@link #runVersion()} is the version it was last written as.
 */
public class GameState {

    public static final int AFFINITY_MIN = -100;
    public static final int AFFINITY_MAX = 100;
    /** Skill slots in the battle menu (V2_SPEC §4.6). */
    public static final int LOADOUT_SLOTS = 4;

    private final String sessionId;
    private final int playerId;
    private @Nullable String currentNode;       // the dialogue node shown; null in WORLD mode
    private Mode mode;
    private WorldPosition position;              // the last validated position (§4.11)
    private long lastWorldEventMillis;           // server time of the last accepted world event
    private final Stats stats;
    private final Inventory inventory;
    private final Map<String, Integer> flags = new HashMap<>();
    private final EnumMap<Faction, Integer> reputation = new EnumMap<>(Faction.class);
    private final Map<String, Integer> affinity = new HashMap<>();
    private final Map<String, QuestStatus> quests = new HashMap<>();
    private final Deque<Step> history = new ArrayDeque<>();
    private @Nullable BattleState battle;        // the battle in progress; null outside BATTLE mode
    private @Nullable BattleState finishedBattle; // a battle that just ended, shown once in the next view
    private final List<String> knownSkills = new ArrayList<>();
    private final String[] loadout = new String[LOADOUT_SLOTS];
    private final CreepRoster creeps = new CreepRoster();
    private final RecentActions recentActions = new RecentActions();
    private @Nullable ActionKey pendingKey;       // the key of the action being applied, remembered on write
    private long rngSeed;
    private Random rng;
    private long sessionStartMillis;
    private final int playSecondsBefore;
    private final Queue<Toast> pendingToasts = new ArrayDeque<>();
    private final boolean demoMode;
    private EventBus bus;                       // one per session, wired by GameEngine
    private boolean offerReload;                // true right after a boss loss (S3)
    private int endingSeconds;                  // set when an ending is reached
    private int endingsFound;                   // set when an ending is reached
    private long runVersion;                    // optimistic-locking version in active_run

    /**
     * @param node     the dialogue node to show, or null to start in the world
     * @param position where the player stands
     */
    public GameState(String sessionId, int playerId, @Nullable String node, WorldPosition position, Stats stats,
                     Inventory inventory, long rngSeed, boolean demoMode, int playSecondsBefore) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.currentNode = node;
        this.mode = node == null ? Mode.WORLD : Mode.DIALOGUE;
        this.position = position;
        this.stats = stats;
        this.inventory = inventory;
        this.rngSeed = rngSeed;
        this.rng = new Random(rngSeed);
        this.demoMode = demoMode;
        this.playSecondsBefore = playSecondsBefore;
        this.sessionStartMillis = System.currentTimeMillis();
        for (Faction faction : Faction.values()) {
            reputation.put(faction, 0);
        }
    }

    // =====================================================================
    // Simple getters
    // =====================================================================
    public String sessionId()      { return sessionId; }
    public int playerId()          { return playerId; }
    public @Nullable String currentNode() { return currentNode; }
    public WorldPosition position()  { return position; }
    public long lastWorldEventMillis() { return lastWorldEventMillis; }
    public Mode mode()             { return mode; }
    public Stats stats()           { return stats; }
    public Inventory inventory()   { return inventory; }
    public @Nullable BattleState battle() { return battle; }
    public CreepRoster creeps()    { return creeps; }
    public RecentActions recentActions() { return recentActions; }
    public long rngSeed()          { return rngSeed; }
    public Random rng()            { return rng; }
    public boolean demoMode()      { return demoMode; }
    public EventBus bus()          { return bus; }
    public boolean offerReload()   { return offerReload; }

    /** Seconds played across all sessions of this save. O(1). */
    public int playSeconds() {
        long thisSession = (System.currentTimeMillis() - sessionStartMillis) / 1000;
        return playSecondsBefore + (int) thisSession;
    }

    // =====================================================================
    // Intent methods used by rules, quests, combat (public API)
    // =====================================================================

    /** Flag value; a missing flag counts as 0 (spec §6.2). O(1). */
    public int flag(String key) {
        return flags.getOrDefault(key, 0);
    }

    public void setFlag(String key, int value) {
        flags.put(key, value);
    }

    /** Reputation with a faction (always present, starts at 0). O(1). */
    public int reputation(Faction faction) {
        return reputation.get(faction);
    }

    /** Adds to reputation, clamped to −100…100. Returns the real change. O(1). */
    public int addReputation(Faction faction, int delta) {
        int before = reputation.get(faction);
        int after = clamp(before + delta);
        reputation.put(faction, after);
        return after - before;
    }

    /** Affinity with an NPC; missing counts as 0. O(1). */
    public int affinity(String npcId) {
        return affinity.getOrDefault(npcId, 0);
    }

    /** Adds to affinity, clamped to −100…100. Returns the real change. O(1). */
    public int addAffinity(String npcId, int delta) {
        int before = affinity(npcId);
        int after = clamp(before + delta);
        affinity.put(npcId, after);
        return after - before;
    }

    /** Status of a quest; unknown quests count as LOCKED. O(1). */
    public QuestStatus questStatus(String questId) {
        return quests.getOrDefault(questId, QuestStatus.LOCKED);
    }

    /** Called only by QuestManager, which enforces the state machine (D14). O(1). */
    public void setQuestStatus(String questId, QuestStatus status) {
        quests.put(questId, status);
    }

    // =====================================================================
    // Skills (V2_SPEC §4.6, P-55)
    // =====================================================================

    /** The skill in each of the four slots, null where empty. */
    public List<String> loadout() {
        return Collections.unmodifiableList(Arrays.asList(loadout.clone()));
    }

    public List<String> knownSkills() {
        return List.copyOf(knownSkills);
    }

    /**
     * Learns a skill and puts it in the first empty slot (a full loadout keeps
     * it known; loadouts change at Lanterns in V5). Returns false if it was
     * already known. O(1).
     */
    public boolean learnSkill(String skillId) {
        if (knownSkills.contains(skillId)) {
            return false;
        }
        knownSkills.add(skillId);
        for (int i = 0; i < loadout.length; i++) {
            if (loadout[i] == null) {
                loadout[i] = skillId;
                break;
            }
        }
        return true;
    }

    /** Puts stored skills back (load only). */
    void restoreSkills(List<String> known, List<String> slots) {
        knownSkills.clear();
        knownSkills.addAll(known);
        Arrays.fill(loadout, null);
        for (int i = 0; i < Math.min(slots.size(), loadout.length); i++) {
            loadout[i] = slots.get(i);
        }
    }

    /** Queues a toast for the next SessionView. O(1). */
    public void addToast(Toast toast) {
        pendingToasts.add(toast);
    }

    // =====================================================================
    // Read-only views (for persistence, views and the inspector)
    // =====================================================================
    public Map<String, Integer> flagsView()          { return Collections.unmodifiableMap(flags); }
    public Map<Faction, Integer> reputationView()    { return Collections.unmodifiableMap(reputation); }
    public Map<String, Integer> affinityView()       { return Collections.unmodifiableMap(affinity); }
    public Map<String, QuestStatus> questsView()     { return Collections.unmodifiableMap(quests); }

    /** History copied oldest-first (step 1 first) for saving (S7). Time O(n), space O(n). */
    public List<Step> historyOldestFirst() {
        List<Step> result = new ArrayList<>(history.size());
        Iterator<Step> it = history.descendingIterator();
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    /** Up to {@code limit} most recent steps, newest first (journal). Time O(limit). */
    public List<Step> recentHistory(int limit) {
        List<Step> result = new ArrayList<>();
        for (Step step : history) {
            if (result.size() == limit) {
                break;
            }
            result.add(step);
        }
        return result;
    }

    // =====================================================================
    // Package-private: only GameEngine (same package) changes these (D30)
    // =====================================================================
    Deque<Step> historyStack()               { return history; }
    Queue<Toast> toastQueue()                { return pendingToasts; }
    void setCurrentNode(@Nullable String nodeId) { this.currentNode = nodeId; }

    /** Moves the player to a validated position at server time {@code millis}. */
    void moveTo(WorldPosition position, long millis) {
        this.position = position;
        this.lastWorldEventMillis = millis;
    }
    void setMode(Mode mode)                  { this.mode = mode; }
    void setBattle(@Nullable BattleState battle) { this.battle = battle; }

    /** A battle that just ended: the next view shows its last round once. */
    void setFinishedBattle(@Nullable BattleState finished) { this.finishedBattle = finished; }

    /** Takes the just-ended battle for one view (like toasts). */
    @Nullable BattleState takeFinishedBattle() {
        BattleState finished = finishedBattle;
        finishedBattle = null;
        return finished;
    }

    @Nullable ActionKey pendingKey()          { return pendingKey; }
    void setPendingKey(@Nullable ActionKey key) { this.pendingKey = key; }
    void setBus(EventBus bus)                { this.bus = bus; }
    void setOfferReload(boolean offer)       { this.offerReload = offer; }
    int endingSeconds()                      { return endingSeconds; }
    int endingsFound()                       { return endingsFound; }

    void recordEnding(int seconds, int found) {
        this.endingSeconds = seconds;
        this.endingsFound = found;
    }

    /** Restores a saved reputation value (load only). */
    void putReputation(Faction faction, int value) { reputation.put(faction, clamp(value)); }

    /** Restores a saved affinity value (load only). */
    void putAffinity(String npcId, int value)       { affinity.put(npcId, clamp(value)); }

    // =====================================================================
    // Saving support (D7, D24)
    // =====================================================================

    /**
     * Continues the current random sequence into a fresh seed and restarts
     * the generator from it (D7). After this, the live game and a save made
     * now produce identical rolls. Returns the new seed. O(1).
     */
    public long reseedForSave() {
        long fresh = rng.nextLong();
        rngSeed = fresh;
        rng = new Random(fresh);
        return fresh;
    }

    // =====================================================================
    // Durable runs (P-22)
    // =====================================================================

    /** The stored version this in-memory copy was last written as (0 = never stored). */
    public long runVersion()               { return runVersion; }

    void setRunVersion(long runVersion)    { this.runVersion = runVersion; }

    private static int clamp(int value) {
        return Math.max(AFFINITY_MIN, Math.min(AFFINITY_MAX, value));
    }
}
