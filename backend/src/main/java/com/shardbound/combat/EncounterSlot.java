package com.shardbound.combat;

/** One encounter_enemy row: which enemy, and how many of it. */
public record EncounterSlot(String enemyId, int qty) {
}
