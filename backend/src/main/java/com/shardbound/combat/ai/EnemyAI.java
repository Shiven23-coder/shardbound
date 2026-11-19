package com.shardbound.combat.ai;

import com.shardbound.combat.Action;
import com.shardbound.combat.CombatState;
import com.shardbound.combat.Enemy;

/**
 * Strategy pattern: how an enemy picks its action. Each enemy gets its own
 * AI instance because some strategies remember what they did before.
 */
public interface EnemyAI {

    Action decide(Enemy self, CombatState combat);

    /**
     * The one bit of memory a strategy may keep between turns (for example
     * "has already defended"), so a fight can be stored and resumed.
     */
    default boolean memory() {
        return false;
    }

    /** Restores what {@link #memory()} returned when the fight was stored. */
    default void restoreMemory(boolean memory) {
    }

    /** Factory for the ai column values AGGRESSIVE / DEFENSIVE / BOSS. O(1). */
    static EnemyAI forType(String type) {
        return switch (type) {
            case "AGGRESSIVE" -> new AggressiveAI();
            case "DEFENSIVE" -> new DefensiveAI();
            case "BOSS" -> new BossAI();
            default -> throw new IllegalArgumentException("Unknown enemy AI: " + type);
        };
    }
}
