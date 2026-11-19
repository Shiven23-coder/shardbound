package com.shardbound.save;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Everything a save slot stores, as plain ids and numbers (no content
 * objects). Written to PostgreSQL as one JSONB document in save format
 * {@link SaveFormat#CURRENT_VERSION}; older documents are upgraded by
 * {@link SaveUpgrader} before they are read into this record.
 *
 * @param quests  quest id → status name, e.g. "ACTIVE"
 * @param history oldest step first
 */
public record SaveSnapshot(
        String currentNode,
        int hp,
        int energy,
        int level,
        int xp,
        int gold,
        List<ItemEntry> inventory,
        @Nullable String equipped,
        Map<String, Integer> flags,
        Map<String, Integer> reputation,
        Map<String, Integer> affinity,
        Map<String, String> quests,
        List<StepEntry> history,
        long rngSeed,
        boolean demoMode,
        int playSeconds) {

    public SaveSnapshot {
        inventory = List.copyOf(inventory);
        flags = Map.copyOf(flags);
        reputation = Map.copyOf(reputation);
        affinity = Map.copyOf(affinity);
        quests = Map.copyOf(quests);
        history = List.copyOf(history);
    }

    /** One inventory stack. */
    public record ItemEntry(String itemId, int qty) { }

    /** One journal step: the node left and the choice taken there. */
    public record StepEntry(String nodeId, int choiceId) { }
}
