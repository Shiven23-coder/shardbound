package com.shardbound.quest;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.item.Item;
import com.shardbound.rules.Rewards;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the quest definitions and the quest state machine
 * (LOCKED → AVAILABLE → ACTIVE → COMPLETED | FAILED, spec §6.5).
 *
 * <p>Shared by all sessions: it holds only immutable definitions; each
 * player's statuses live in their GameState.
 *
 * <p>Invalid transitions never throw during play (D14): they log a warning
 * and leave the quest unchanged. StoryValidator catches what it can at import.
 */
public class QuestManager {

    private static final Logger LOG = LoggerFactory.getLogger(QuestManager.class);

    private final Map<String, Quest> quests;      // insertion order = content order
    private final Map<String, Item> items;        // for reward items
    private List<String> logOrder;                // cached topoOrder(), set at startup

    public QuestManager(Map<String, Quest> quests, Map<String, Item> items) {
        this.quests = Collections.unmodifiableMap(new LinkedHashMap<>(quests));
        this.items = items;
    }

    public Map<String, Quest> quests() {
        return quests;
    }

    public Quest quest(String questId) {
        return quests.get(questId);
    }

    // =====================================================================
    // Student-implemented (CLAUDE.md rule 8)
    // =====================================================================

    /**
     * Topological order of all quests using <b>Kahn's algorithm</b>.
     *
     * <p>The quests form a directed graph with an edge {@code requires → quest}
     * for every prerequisite. A quest may only appear after all of its
     * prerequisites.
     *
     * <ol>
     *   <li>Compute each quest's in-degree (= number of prerequisites).</li>
     *   <li>Put every quest with in-degree 0 into a FIFO queue, in content
     *       order (the iteration order of {@link #quests()}).</li>
     *   <li>Repeatedly remove the front quest, append it to the result, and
     *       decrease the in-degree of every quest that requires it; when one
     *       reaches 0, add it to the back of the queue. Visit dependants in
     *       content order so the result is deterministic.</li>
     *   <li>If the result has fewer quests than exist, the remaining quests
     *       are in (or depend on) a cycle: throw
     *       {@link ContentValidationException} naming them.</li>
     * </ol>
     *
     * <p>You may assume every prerequisite id refers to an existing quest
     * (ContentAssembler checks that first).
     *
     * <p>Expected complexity: time O(V + E), space O(V + E), where V is the
     * number of quests and E the number of prerequisite links.
     * Runs once at startup; the quest log displays quests in this order.
     *
     * @return quest ids in a valid dependency order
     * @throws ContentValidationException if the prerequisites contain a cycle
     */
    public List<String> topoOrder() {
        // TODO(student): implement Kahn's algorithm (see QuestManagerTest)
        throw new UnsupportedOperationException("TODO(student): QuestManager.topoOrder");
    }

    // =====================================================================
    // Startup
    // =====================================================================

    /** Runs topoOrder() once at startup and caches it for the quest log. O(V + E). */
    public void computeOrder() {
        this.logOrder = List.copyOf(topoOrder());
    }

    /** The cached topological order. Call {@link #computeOrder()} at startup first. O(1). */
    public List<String> logOrder() {
        if (logOrder == null) {
            throw new IllegalStateException("QuestManager.computeOrder() was not called at startup");
        }
        return logOrder;
    }

    // =====================================================================
    // State machine
    // =====================================================================

    /** New game: no prerequisites → AVAILABLE, otherwise LOCKED (D14). Time O(V). */
    public void initialStates(GameState state) {
        for (Quest quest : quests.values()) {
            QuestStatus start = quest.prereqs().isEmpty() ? QuestStatus.AVAILABLE : QuestStatus.LOCKED;
            state.setQuestStatus(quest.id(), start);
        }
    }

    /** AVAILABLE → ACTIVE. Anything else is logged and ignored (D14). O(1). */
    public void start(GameState state, String questId, EventBus bus) {
        if (!checkTransition(state, questId, QuestStatus.AVAILABLE, "start")) {
            return;
        }
        state.setQuestStatus(questId, QuestStatus.ACTIVE);
        bus.publish(GameEvent.of(EventType.QUEST_STARTED,
                "New quest: " + quests.get(questId).title(), "questId", questId));
    }

    /**
     * ACTIVE → COMPLETED, grants rewards, then unlocks any quest whose
     * prerequisites are now all complete. Time O(V · P) for the unlock scan
     * (P = prerequisites per quest), space O(1).
     */
    public void complete(GameState state, String questId, EventBus bus) {
        if (!checkTransition(state, questId, QuestStatus.ACTIVE, "complete")) {
            return;
        }
        Quest quest = quests.get(questId);
        state.setQuestStatus(questId, QuestStatus.COMPLETED);
        bus.publish(GameEvent.of(EventType.QUEST_COMPLETED,
                "Quest complete: " + quest.title(), "questId", questId));
        grantRewards(state, bus, quest);
        refreshAvailability(state);
    }

    /** ACTIVE → FAILED (D14). O(1). */
    public void fail(GameState state, String questId, EventBus bus) {
        if (!checkTransition(state, questId, QuestStatus.ACTIVE, "fail")) {
            return;
        }
        state.setQuestStatus(questId, QuestStatus.FAILED);
        bus.publish(GameEvent.of(EventType.QUEST_FAILED,
                "Quest failed: " + quests.get(questId).title(), "questId", questId));
    }

    /**
     * LOCKED → AVAILABLE for every quest whose prerequisites are all COMPLETED
     * (re-evaluated after every completion, spec §6.5). Time O(V · P).
     */
    void refreshAvailability(GameState state) {
        for (Quest quest : quests.values()) {
            if (state.questStatus(quest.id()) == QuestStatus.LOCKED && allPrereqsDone(state, quest)) {
                state.setQuestStatus(quest.id(), QuestStatus.AVAILABLE);
            }
        }
    }

    private boolean allPrereqsDone(GameState state, Quest quest) {
        for (String prereq : quest.prereqs()) {
            if (state.questStatus(prereq) != QuestStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    private void grantRewards(GameState state, EventBus bus, Quest quest) {
        Rewards.grantXp(state, bus, quest.rewardXp());
        if (quest.rewardGold() > 0) {
            Rewards.grantGold(state, bus, quest.rewardGold());
        }
        if (quest.rewardItem() != null) {
            Rewards.grantItem(state, bus, items.get(quest.rewardItem()), 1);
        }
    }

    /** True if the quest exists and is in {@code required}; otherwise logs a warning (D14). */
    private boolean checkTransition(GameState state, String questId, QuestStatus required, String action) {
        if (!quests.containsKey(questId)) {
            LOG.warn("Ignored {} of unknown quest '{}'", action, questId);
            return false;
        }
        QuestStatus current = state.questStatus(questId);
        if (current != required) {
            LOG.warn("Ignored {} of quest '{}': status is {}, expected {}", action, questId, current, required);
            return false;
        }
        return true;
    }
}
