package com.shardbound.combat;

import java.util.List;

/**
 * An unfinished fight as plain data, stored with the active run (P-22).
 * Enemies are rebuilt from the encounter in content, so only what changed
 * during the fight is kept: hit points, guard, turns taken and AI memory.
 */
public record CombatSnapshot(
        String encounterId,
        int round,
        List<String> log,
        List<Hit> lastHits,
        boolean playerDefending,
        List<EnemyEntry> enemies) {

    public CombatSnapshot {
        log = List.copyOf(log);
        lastHits = List.copyOf(lastHits);
        enemies = List.copyOf(enemies);
    }

    /** The changing part of one enemy, in encounter order. */
    public record EnemyEntry(int hp, boolean defending, int turnsTaken, boolean aiMemory) { }
}
