package com.shardbound.combat;

/**
 * A combat intent. {@code target} is an enemy index (ignored when not
 * attacking); {@code itemId} is only used by ITEM.
 */
public record Action(ActionType type, int target, String itemId) {

    public static Action of(ActionType type) {
        return new Action(type, 0, null);
    }
}
