package com.shardbound.view;

import org.jspecify.annotations.Nullable;

/**
 * A notification: kind is ITEM, LOOT, REP, AFFINITY, QUEST, LEVEL, GOLD, XP, HEAL, COMBAT, ACHIEVEMENT or INFO.
 * A LOOT toast (P-65) also names what was found ({@code subject}, inside {@code text}), the weapon's
 * {@code tier}, and whether it has a higher attack rating than the equipped weapon ({@code upgrade},
 * computed by the server); the others leave them null and false.
 */
public record ToastView(String kind, String text, @Nullable String tier, @Nullable String subject, boolean upgrade) {
}
