package com.shardbound.combat.ai;

import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.combat.CombatState;
import com.shardbound.combat.Enemy;

/** Always attacks. */
public class AggressiveAI implements EnemyAI {

    @Override
    public Action decide(Enemy self, CombatState combat) {
        return Action.of(ActionType.ATTACK);
    }
}
