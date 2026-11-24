package com.shardbound.event;

/**
 * Everything that can happen in a game. The first 13 come from spec §6.8;
 * the last five were added so that EVERY effect publishes an event
 * (spec §6.2 "Every effect publishes an event, which produces a toast").
 */
public enum EventType {
    NODE_ENTERED,
    ITEM_GAINED,
    ITEM_EQUIPPED,
    REP_CHANGED,
    AFFINITY_CHANGED,
    QUEST_STARTED,
    QUEST_COMPLETED,
    QUEST_FAILED,
    BATTLE_WON,
    BATTLE_LOST,
    LOCKED_CHOICE_PASSED,
    ENDING_REACHED,
    // added for effects that had no event type
    ITEM_LOST,
    FLAG_SET,
    GOLD_CHANGED,
    HEALED,
    SHARDS_GAINED,
    SKILL_LEARNED,
    // added for V5 progression (P-61)
    LEVEL_UP,
    // added for V6a chests (P-65)
    CHEST_OPENED
}
