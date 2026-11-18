package com.shardbound.rules;

import com.shardbound.engine.GameState;

/**
 * A single requirement on the game state. Sealed: the compiler knows every
 * implementation, so a switch over conditions must handle all six kinds.
 */
public sealed interface Condition
        permits FlagCondition, RepCondition, AffinityCondition, ItemCondition, QuestCondition, StatCondition {

    /** True if the state meets the requirement. O(1). */
    boolean test(GameState state);

    /** The requirement in words, without "Requires", e.g. "Eldmere reputation ≥ 20". */
    String requirement();

    /** Lock-reason text shown on a locked choice, e.g. "Requires Eldmere reputation ≥ 20". */
    default String describe() {
        return "Requires " + requirement();
    }
}
