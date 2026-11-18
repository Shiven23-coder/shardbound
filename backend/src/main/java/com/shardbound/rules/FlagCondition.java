package com.shardbound.rules;

import com.shardbound.engine.GameState;

/** Compares a story flag; a missing flag counts as 0. */
public record FlagCondition(String key, Op op, int value) implements Condition {

    @Override
    public boolean test(GameState state) {
        return op.compare(state.flag(key), value);
    }

    @Override
    public String requirement() {
        return "flag " + key + " " + op.pretty() + " " + value;
    }
}
