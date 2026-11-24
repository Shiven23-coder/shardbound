package com.shardbound.engine;

import com.shardbound.event.GameEvent;
import org.jspecify.annotations.Nullable;

/**
 * A short notification shown by the UI ("Obtained: Heartshard"). A LOOT toast (P-65) names what was found
 * ({@code subject}, shown in its {@code tier} colour) and whether that weapon would raise the player's
 * attack rating ({@code upgrade}).
 */
public record Toast(String kind, String text, @Nullable String tier, @Nullable String subject, boolean upgrade) {

    public Toast(String kind, String text) {
        this(kind, text, null, null, false);
    }

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
            case BATTLE_WON, BATTLE_LOST -> "BATTLE";
            case GOLD_CHANGED -> "GOLD";
            case SHARDS_GAINED -> "SHARDS";
            case SKILL_LEARNED -> "SKILL";
            case HEALED -> "HEAL";
            case LEVEL_UP -> "LEVEL";
            default -> "INFO";
        };
        return new Toast(kind, message);
    }
}
