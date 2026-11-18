package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.model.Faction;

/** Compares reputation with a faction. */
public record RepCondition(Faction faction, Op op, int value) implements Condition {

    @Override
    public boolean test(GameState state) {
        return op.compare(state.reputation(faction), value);
    }

    @Override
    public String requirement() {
        return faction.displayName() + " reputation " + op.pretty() + " " + value;
    }
}
