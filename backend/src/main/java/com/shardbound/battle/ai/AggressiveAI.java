package com.shardbound.battle.ai;

import com.shardbound.battle.BattleRules;
import com.shardbound.battle.CreepFighter;
import com.shardbound.battle.Fighter;

import java.util.Random;

/** Always attacks, with a move picked by weight. */
public final class AggressiveAI implements EnemyAI {

    @Override
    public Choice decide(CreepFighter self, Fighter foe, BattleRules rules, Random rng) {
        return new Choice(EnemyAI.weighted(self.template().moves(), rng));
    }
}
