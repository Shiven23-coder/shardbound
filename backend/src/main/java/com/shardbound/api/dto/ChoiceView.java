package com.shardbound.api.dto;

/** One choice as shown to the player. lockReason is null when unlocked. */
public record ChoiceView(int id, String label, boolean locked, String lockReason) {
}
