package com.shardbound.battle.ai;

import com.shardbound.battle.BattleRules;
import com.shardbound.battle.CreepFighter;
import com.shardbound.battle.CreepTemplate;
import com.shardbound.battle.Fighter;

import java.util.List;
import java.util.Random;

/** Prefers a status move whose status the foe does not carry yet; otherwise attacks by weight. */
public final class AfflicterAI implements EnemyAI {

    @Override
    public Choice decide(CreepFighter self, Fighter foe, BattleRules rules, Random rng) {
        List<CreepTemplate.WeightedMove> fresh = self.template().moves().stream()
                .filter(m -> m.skill().status() != null && !foe.statuses().has(m.skill().status()))
                .toList();
        return new Choice(EnemyAI.weighted(fresh.isEmpty() ? self.template().moves() : fresh, rng));
    }
}
