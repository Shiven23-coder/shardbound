package com.shardbound.persistence;

import java.util.List;
import java.util.Map;

/**
 * One save slot as raw rows: ids and numbers only, no content objects (D22).
 * GameEngine turns this back into a GameState using the in-memory catalog,
 * so the persistence package never depends on game classes.
 *
 * @param quests   quest_id → status name (e.g. "ACTIVE")
 * @param history  oldest step first (step_no ascending)
 */
public record SaveData(int saveId, int playerId, int slot, String currentNode,
                       int hp, int energy, int level, int xp, int gold,
                       long rngSeed, boolean demoMode, int playSeconds,
                       List<InventoryRow> inventory,
                       Map<String, Integer> flags,
                       Map<String, Integer> reputation,
                       Map<String, Integer> affinity,
                       Map<String, String> quests,
                       List<HistoryRow> history) {

    public SaveData {
        inventory = List.copyOf(inventory);
        flags = Map.copyOf(flags);
        reputation = Map.copyOf(reputation);
        affinity = Map.copyOf(affinity);
        quests = Map.copyOf(quests);
        history = List.copyOf(history);
    }

    /** One save_inventory row. */
    public record InventoryRow(String itemId, int qty, boolean equipped) { }

    /** One save_history row. choiceId is 0 when the choice was removed (SET NULL, D1). */
    public record HistoryRow(int stepNo, String nodeId, int choiceId) { }

    /** Summary for the save/load screen (slot list). */
    public record SlotSummary(int slot, String currentNode, int level, int playSeconds, String updatedAt) { }
}
