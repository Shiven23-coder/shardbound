package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A boss from content/bosses.json (V2_SPEC §4.7, P-67): base stats at level 1 that scale like a creep's, a
 * fixed level, two phases, what a guarded telegraph does to it, its reward and the flag its defeat sets.
 * Immutable and shared (D20).
 *
 * @param exposeStatus  the status a guarded telegraphed blow puts on the boss ("Guard the fall")
 * @param fallbackLevel the reward when the item is already owned or the bag is full: Shards of one creep of
 *                      this level (P-65)
 */
public record BossTemplate(String id, String name, Family family, String sprite, int level, int baseHp, int baseAtk,
                           int baseDef, int baseSpd, List<Phase> phases, Status exposeStatus, String exposeLine,
                           String rewardItem, int fallbackLevel, String defeatFlag, String introCutscene,
                           String epilogueCutscene) {

    public BossTemplate {
        phases = List.copyOf(phases);
        if (phases.size() != 2) {
            throw new IllegalArgumentException("A boss has two phases: " + id);
        }
    }

    /**
     * One phase: it begins when the boss's HP falls to {@code atPct} % (the first phase from the start), with
     * its line and speed change, its weighted moves, and a telegraphed move announced every
     * {@code telegraphEvery}-th round.
     */
    public record Phase(int atPct, @Nullable String line, int spdDelta, List<CreepTemplate.WeightedMove> moves,
                        Skill telegraph, int telegraphEvery) {

        public Phase {
            moves = List.copyOf(moves);
        }
    }

    /** The boss as a creep at its first phase, so a fighter can be built from it. */
    public CreepTemplate asCreep() {
        return new CreepTemplate(id, name, family, sprite, baseHp, baseAtk, baseDef, baseSpd,
                CreepTemplate.Strategy.AGGRESSIVE, phases.getFirst().moves(), List.of());
    }
}
