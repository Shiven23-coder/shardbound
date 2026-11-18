package com.shardbound.rules;

import com.shardbound.engine.GameState;

/** Compares affinity with an NPC; a missing value counts as 0. */
public record AffinityCondition(String npcId, String npcName, Op op, int value) implements Condition {

    @Override
    public boolean test(GameState state) {
        return op.compare(state.affinity(npcId), value);
    }

    @Override
    public String requirement() {
        return "affinity with " + npcName + " " + op.pretty() + " " + value;
    }
}
