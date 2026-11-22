package com.shardbound.view;

import org.jspecify.annotations.Nullable;

/**
 * HUD and character-sheet numbers: HP, FP, gold, Shards (V2_SPEC §4.3), level,
 * the attack rating with the equipped weapon and defence. weapon is null when
 * nothing is equipped.
 */
public record HudView(int hp, int maxHp, int fp, int maxFp, int gold, int shards, int level, int attack, int defence,
                      @Nullable WeaponInfo weapon) {

    /** {@code base} is the weapon's base attack. */
    public record WeaponInfo(String id, String name, String tier, int base) { }
}
