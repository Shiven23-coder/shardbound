package com.shardbound.combat;

/**
 * Immutable enemy content loaded once at startup and shared by every session
 * (D20). CombatEngine builds a fresh, mutable {@link Enemy} from it per fight.
 */
public record EnemyTemplate(String id, String name, int hp, int atk, int def, int spd,
                            int xpReward, int goldReward, String ai) {
}
