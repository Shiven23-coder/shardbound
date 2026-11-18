package com.shardbound.combat;

import com.shardbound.item.Weapon;

import java.util.Random;

/**
 * The damage formula (spec §6.6), kept in one small class so it can be unit
 * tested with a seeded or fixed Random:
 * <pre>
 * base = attacker.atk * weapon.tier.multiplier + weapon.attack   (no weapon: atk only, multiplier 1)
 * base = base * actionMultiplier                                   (SKILL ×1.8, boss heavy ×1.6 — S1)
 * roll = base * (0.9 + rng.nextDouble() * 0.2)                     (±10%)
 * crit = rng.nextInt(100) &lt; critPct → ×1.5
 * dmg  = max(1, round(roll * crit) - defender.def * (defending ? 2 : 1))
 * </pre>
 * The random calls always happen in this order (nextDouble, then nextInt),
 * which is what makes seeded fights reproducible.
 */
public class DamageCalculator {

    public static final double CRIT_MULTIPLIER = 1.5;
    public static final double SKILL_MULTIPLIER = 1.8;
    public static final double HEAVY_MULTIPLIER = 1.6;
    /** Enemies carry no weapon; the spec gives no crit chance, so they use a small fixed one. */
    public static final int ENEMY_CRIT_PCT = 5;

    /**
     * Rolls damage from attacker to defender. {@code weapon} may be null
     * (enemies, or an unarmed player). Time O(1), space O(1).
     */
    public DamageRoll roll(Combatant attacker, Combatant defender, Weapon weapon, int critPct,
                           double actionMultiplier, Random rng) {
        double base = baseDamage(attacker, weapon) * actionMultiplier;
        double roll = base * (0.9 + rng.nextDouble() * 0.2);
        boolean crit = rng.nextInt(100) < critPct;
        double withCrit = crit ? roll * CRIT_MULTIPLIER : roll;
        int defence = defender.def() * (defender.isDefending() ? 2 : 1);
        int amount = Math.max(1, (int) Math.round(withCrit) - defence);
        return new DamageRoll(amount, crit);
    }

    /** atk × tier multiplier + weapon attack, or plain atk without a weapon. O(1). */
    public double baseDamage(Combatant attacker, Weapon weapon) {
        if (weapon == null) {
            return attacker.atk();
        }
        return attacker.atk() * weapon.tier().multiplier + weapon.attack();
    }
}
