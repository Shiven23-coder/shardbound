package com.shardbound.model;

import java.util.Comparator;

/**
 * Weapon tiers, declared in the SAME order as the PostgreSQL enum
 * {@code weapon_tier} ('F' .. 'SSS'), so ordinal comparison in Java and
 * ORDER BY tier in SQL agree. Each tier multiplies the wielder's base attack.
 */
public enum Tier {
    F(1.00), E(1.10), D(1.20), C(1.35), B(1.50), A(1.70), S(2.00), SS(2.40), SSS(3.00);

    /** Highest tier first. O(1) per comparison. */
    public static final Comparator<Tier> BY_RANK_DESC = Comparator.comparingInt(Tier::ordinal).reversed();

    public final double multiplier;

    Tier(double multiplier) {
        this.multiplier = multiplier;
    }

    /** True if this tier is the same as or better than {@code other}. O(1). */
    public boolean atLeast(Tier other) {
        return this.ordinal() >= other.ordinal();
    }
}
