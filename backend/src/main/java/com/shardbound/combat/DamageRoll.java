package com.shardbound.combat;

/** The result of one DamageCalculator roll. */
public record DamageRoll(int amount, boolean crit) {
}
