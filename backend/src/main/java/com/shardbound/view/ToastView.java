package com.shardbound.view;

/** A notification: kind is ITEM, REP, AFFINITY, QUEST, LEVEL, GOLD, XP, HEAL, COMBAT, ACHIEVEMENT or INFO. */
public record ToastView(String kind, String text) {
}
