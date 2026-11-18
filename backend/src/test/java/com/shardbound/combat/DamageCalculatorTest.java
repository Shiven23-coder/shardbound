package com.shardbound.combat;

import com.shardbound.item.Weapon;
import com.shardbound.model.Stats;
import com.shardbound.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The damage formula (spec §6.6) with fixed and seeded randomness. */
class DamageCalculatorTest {

    /** A Random that always returns the same values, so the formula can be checked exactly. */
    static class FixedRandom extends Random {
        private final double nextDouble;
        private final int nextInt;

        FixedRandom(double nextDouble, int nextInt) {
            this.nextDouble = nextDouble;
            this.nextInt = nextInt;
        }

        @Override public double nextDouble() { return nextDouble; }
        @Override public int nextInt(int bound) { return nextInt; }
    }

    private final DamageCalculator calc = new DamageCalculator();
    private final Weapon ironSword = new Weapon("iron", "Iron Longsword", "", 0, Tier.C, 8, 8, null);

    private static PlayerCombatant player(Weapon w) {
        return new PlayerCombatant("You", Stats.starting(), w);   // atk 6, def 2
    }

    private static Enemy enemy(int def) {
        return Enemy.from(new EnemyTemplate("e", "Target", 50, 5, def, 4, 0, 0, "AGGRESSIVE"), 0, "Target", false);
    }

    @Test
    void baseDamageUsesTierMultiplierPlusWeaponAttack() {
        // 6 × 1.35 + 8 = 16.1
        assertEquals(16.1, calc.baseDamage(player(ironSword), ironSword), 1e-9);
        assertEquals(6.0, calc.baseDamage(player(null), null), 1e-9);
    }

    @Test
    void middleRollNoCrit() {
        // roll = 16.1 × (0.9 + 0.5 × 0.2) = 16.1; round → 16; minus def 1 → 15
        DamageRoll r = calc.roll(player(ironSword), enemy(1), ironSword, 8, 1.0, new FixedRandom(0.5, 99));
        assertEquals(15, r.amount());
        assertFalse(r.crit());
    }

    @Test
    void critMultipliesByOneAndAHalf() {
        // 16.1 × 1.5 = 24.15 → 24; minus 1 → 23. nextInt 0 < critPct 8 → crit
        DamageRoll r = calc.roll(player(ironSword), enemy(1), ironSword, 8, 1.0, new FixedRandom(0.5, 0));
        assertEquals(23, r.amount());
        assertTrue(r.crit());
    }

    @Test
    void skillMultiplierAppliesToBase() {                        // S1
        // 16.1 × 1.8 = 28.98 → roll ×1.0 → 29; minus 1 → 28
        DamageRoll r = calc.roll(player(ironSword), enemy(1), ironSword, 8, DamageCalculator.SKILL_MULTIPLIER,
                new FixedRandom(0.5, 99));
        assertEquals(28, r.amount());
    }

    @Test
    void lowestAndHighestRolls() {
        // low: 16.1 × 0.9 = 14.49 → 14 - 1 = 13; high: 16.1 × 1.1 = 17.71 → 18 - 1 = 17
        assertEquals(13, calc.roll(player(ironSword), enemy(1), ironSword, 0, 1.0, new FixedRandom(0.0, 99)).amount());
        assertEquals(17, calc.roll(player(ironSword), enemy(1), ironSword, 0, 1.0, new FixedRandom(1.0, 99)).amount());
    }

    @Test
    void defendingDoublesDefenceAndDamageIsAtLeastOne() {
        Enemy wall = enemy(20);
        wall.setDefending(true);                                 // def 40
        DamageRoll r = calc.roll(player(null), wall, null, 0, 1.0, new FixedRandom(0.5, 99));
        assertEquals(1, r.amount());
    }

    @Test
    void sameSeedSameDamage() {
        DamageRoll a = calc.roll(player(ironSword), enemy(1), ironSword, 8, 1.0, new Random(1234));
        DamageRoll b = calc.roll(player(ironSword), enemy(1), ironSword, 8, 1.0, new Random(1234));
        assertEquals(a, b);
    }

    @Test
    void seededRollsStayInsideTheFormulaRange() {
        Random rng = new Random(42);
        for (int i = 0; i < 1000; i++) {
            int amount = calc.roll(player(ironSword), enemy(1), ironSword, 8, 1.0, rng).amount();
            assertTrue(amount >= 13 && amount <= 26, "amount " + amount);   // 13..17 normal, up to 26 on crit
        }
    }
}
