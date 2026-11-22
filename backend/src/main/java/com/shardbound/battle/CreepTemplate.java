package com.shardbound.battle;

import java.util.List;

/**
 * An enemy from content/creeps.json: base stats at level 1, its moves and its
 * drops. Immutable and shared (D20); each battle builds its own fighter from it.
 *
 * @param sprite the art manifest key the client draws
 */
public record CreepTemplate(String id, String name, Family family, String sprite, int baseHp, int baseAtk,
                            int baseDef, int baseSpd, Strategy ai, List<WeightedMove> moves, List<Drop> drops) {

    public CreepTemplate {
        moves = List.copyOf(moves);
        drops = List.copyOf(drops);
    }

    /** How the creep picks its move (P-54). */
    public enum Strategy { AGGRESSIVE, DEFENSIVE, AFFLICTER }

    public record WeightedMove(Skill skill, int weight) { }

    public record Drop(String itemId, int pct) { }
}
