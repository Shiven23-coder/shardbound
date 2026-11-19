package com.shardbound.view;

import org.jspecify.annotations.Nullable;

/** One choice as shown to the player. lockReason is null when unlocked. */
public record ChoiceView(int id, String label, boolean locked, @Nullable String lockReason) {
}
