package com.shardbound.engine;

import com.shardbound.event.GameEvent;

/** A short notification shown by the UI ("Obtained: Heartshard"). */
public record Toast(String kind, String text) {

    /**
     * Builds the toast for an event, or returns null if the event has no
     * message (e.g. NODE_ENTERED, FLAG_SET). O(1).
     */
    public static Toast from(GameEvent event) {
        String message = event.message();
        if (message == null) {
            return null;
        }
        String kind = switch (event.type()) {
            case ITEM_GAINED, ITEM_LOST, ITEM_EQUIPPED -> "ITEM";
            case REP_CHANGED -> "REP";
            case AFFINITY_CHANGED -> "AFFINITY";
            case QUEST_STARTED, QUEST_COMPLETED, QUEST_FAILED -> "QUEST";
            case COMBAT_WON, COMBAT_LOST -> "COMBAT";
            case LEVEL_UP -> "LEVEL";
            case GOLD_CHANGED -> "GOLD";
            case XP_GAINED -> "XP";
            case HEALED -> "HEAL";
            default -> "INFO";
        };
        return new Toast(kind, message);
    }
}
