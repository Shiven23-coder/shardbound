package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * One status from content/statuses.json. Percentages are of the affected
 * fighter's max HP.
 *
 * @param burstPct         damage when it takes hold (Bleed)
 * @param tickPct          damage at the start of each of its owner's turns
 * @param defencePct       change to defence while it lasts (Frost −30)
 * @param speed            change to speed while it lasts (Frost −3)
 * @param removedBy        a hit of this element ends it (Burn by Frost, Frost by Fire), or null
 * @param skipsTurn        the owner loses its next turn (Stun)
 * @param immuneTurnsAfter turns of immunity to this status once it ends (Stun)
 * @param immuneFamilies   families it never takes hold on
 */
public record StatusRule(Status status, String name, String letter, String icon, int turns, int burstPct, int tickPct,
                         int defencePct, int speed, @Nullable Element removedBy, boolean skipsTurn,
                         int immuneTurnsAfter, Set<Family> immuneFamilies) {

    public StatusRule {
        immuneFamilies = Set.copyOf(immuneFamilies);
    }

    public boolean affects(Family family) {
        return !immuneFamilies.contains(family);
    }
}
