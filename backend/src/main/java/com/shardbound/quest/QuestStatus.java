package com.shardbound.quest;

/**
 * Quest state machine: LOCKED → AVAILABLE → ACTIVE → COMPLETED | FAILED.
 * Declared in the SAME order as the PostgreSQL enum {@code quest_status};
 * QUEST conditions store the ordinal (D13), and QuestStatusOrderTest keeps
 * the two orders identical.
 */
public enum QuestStatus {
    LOCKED, AVAILABLE, ACTIVE, COMPLETED, FAILED
}
