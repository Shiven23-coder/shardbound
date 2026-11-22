package com.shardbound.battle.ai;

import com.shardbound.battle.BattleRules;
import com.shardbound.battle.CreepFighter;
import com.shardbound.battle.Fighter;

import java.util.Random;

/** Guards once when its HP first drops below the threshold (30 %); otherwise attacks by weight. */
public final class DefensiveAI implements EnemyAI {

    @Override
    public Choice decide(CreepFighter self, Fighter foe, BattleRules rules, Random rng) {
        if (!self.guardedOnce() && self.hp() * 100 < self.maxHp() * rules.defensiveGuardBelowPct()) {
            self.markGuarded();
            return Choice.GUARD;
        }
        return new Choice(EnemyAI.weighted(self.template().moves(), rng));
    }
}
