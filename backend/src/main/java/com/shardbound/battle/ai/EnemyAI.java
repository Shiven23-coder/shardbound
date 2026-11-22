package com.shardbound.battle.ai;

import com.shardbound.battle.BattleRules;
import com.shardbound.battle.CreepFighter;
import com.shardbound.battle.CreepTemplate;
import com.shardbound.battle.Fighter;
import com.shardbound.battle.Skill;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Strategy pattern: how a creep picks its move each turn (P-54). Picks use
 * the run's seeded RNG, so a seed reproduces a battle.
 */
public interface EnemyAI {

    /** A creep's decision: a move, or (move null) to guard. */
    record Choice(@Nullable Skill move) {
        public static final Choice GUARD = new Choice(null);

        public boolean guards() {
            return move == null;
        }
    }

    Choice decide(CreepFighter self, Fighter foe, BattleRules rules, Random rng);

    static EnemyAI forStrategy(CreepTemplate.Strategy strategy) {
        return switch (strategy) {
            case AGGRESSIVE -> new AggressiveAI();
            case DEFENSIVE -> new DefensiveAI();
            case AFFLICTER -> new AfflicterAI();
        };
    }

    /** A move picked by weight: one {@code nextInt(total)} call. O(m). */
    static Skill weighted(List<CreepTemplate.WeightedMove> moves, Random rng) {
        int total = 0;
        for (CreepTemplate.WeightedMove move : moves) {
            total += move.weight();
        }
        int pick = rng.nextInt(total);
        for (CreepTemplate.WeightedMove move : moves) {
            pick -= move.weight();
            if (pick < 0) {
                return move.skill();
            }
        }
        throw new IllegalStateException("Weights must be positive");
    }
}
