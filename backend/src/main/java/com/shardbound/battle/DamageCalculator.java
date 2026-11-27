package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * The V2 damage formula (V2_SPEC §4.7):
 * <pre>
 * raw      = (weapon move ? AR : spell power) · power      creeps: atk · power
 * elem     = chart[defender.family][move.element]           W 1.5 · N 1 · R 0.5 · I 0
 * roll     = 0.9 + rng.nextDouble() · 0.2
 * crit     = rng.nextInt(100) &lt; crit% ? 1.5 : 1.0
 * mitigate = 100 / (100 + DEF)                              DEF after statuses (Frost −30 %)
 * guard    = guarding ? (telegraphed ? 0.25 : 0.5) : 1.0
 * damage   = elem == 0 ? 0 : max(1, round(raw · elem · roll · crit · mitigate · guard))
 * status   = rng.nextInt(100) &lt; chance ? apply : —         only if the move carries one and elem &gt; 0
 * </pre>
 * The order of random calls (roll, crit, status) never changes, so a seed
 * reproduces a battle. Stateless.
 */
public final class DamageCalculator {

    /**
     * The result of one hit.
     *
     * @param affinity how the defender's family takes the move's element
     * @param status   the status the hit put on, or null
     */
    public record Hit(int amount, boolean crit, Affinity affinity, @Nullable Status status) { }

    private final BattleContent content;

    public DamageCalculator(BattleContent content) {
        this.content = content;
    }

    public Hit hit(Fighter attacker, Fighter defender, Skill move, boolean telegraphed, Random rng) {
        BattleRules rules = content.rules();
        Affinity affinity = content.chart().affinity(defender.family(), move.element());
        double elem = content.chart().multiplier(defender.family(), move.element());
        double roll = rules.rollMin() + rng.nextDouble() * (rules.rollMax() - rules.rollMin());
        boolean crit = rng.nextInt(100) < attacker.critPct();
        double mitigate = 100.0 / (100.0 + defender.defence(content));
        double guard = defender.guarding() ? (telegraphed ? rules.guardTelegraphed() : rules.guardNormal()) : 1.0;
        int amount = elem == 0 ? 0 : (int) Math.max(1, Math.round(
                attacker.raw(move) * elem * roll * (crit ? rules.critMultiplier() : 1.0) * mitigate * guard));
        Status applied = null;
        if (move.status() != null && elem > 0) {
            int chance = Math.max(0, Math.min(rules.statusChanceCap(),
                    move.statusChance() + attacker.statusBonusPct() - defender.statusResistPct()));
            if (defender instanceof CreepFighter creep && creep.boss() != null) {
                chance /= 2;                                            // halved against bosses (§4.5)
            }
            if (rng.nextInt(100) < chance) {
                StatusRule rule = content.status(move.status());
                if (defender.statuses().canTake(rule, defender.family())) {
                    applied = move.status();
                }
            }
        }
        return new Hit(amount, crit, affinity, applied);
    }
}
