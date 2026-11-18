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
