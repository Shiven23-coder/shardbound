package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

/**
 * The player's choice for a round (V2_SPEC §7): STRIKE or GUARD (the ATTACK
 * menu), a SKILL from a loadout slot (0–3), an ITEM from the bag, or FLEE.
 */
public record BattleAction(Kind kind, int slot, @Nullable String itemId) {

    public enum Kind { STRIKE, GUARD, SKILL, ITEM, FLEE }

    public static BattleAction of(Kind kind) {
        return new BattleAction(kind, 0, null);
    }

    public static BattleAction skill(int slot) {
        return new BattleAction(Kind.SKILL, slot, null);
    }

    public static BattleAction item(String itemId) {
        return new BattleAction(Kind.ITEM, 0, itemId);
    }
}
