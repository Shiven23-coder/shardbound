package com.shardbound.rules;

import com.shardbound.engine.GameState;

import java.util.Set;

/** Compares a player stat: "level", "gold" or "hp" (spec §6.2). */
public record StatCondition(String stat, Op op, int value) implements Condition {

    /** The stat names a STAT condition may use. */
    public static final Set<String> STATS = Set.of("level", "gold", "hp");

    @Override
    public boolean test(GameState state) {
        int actual = switch (stat) {
            case "level" -> state.stats().level();
            case "gold" -> state.stats().gold();
            case "hp" -> state.stats().hp();
            default -> throw new IllegalStateException("Unknown stat: " + stat);
        };
        return op.compare(actual, value);
    }

    @Override
    public String requirement() {
        return stat + " " + op.pretty() + " " + value;
    }
}
