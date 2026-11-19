package com.shardbound.combat.ai;

import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.combat.CombatState;
import com.shardbound.combat.Enemy;

/** Defends ONCE when below 30% hp; otherwise attacks (spec §6.6). */
public class DefensiveAI implements EnemyAI {

    private boolean hasDefended;

    @Override
    public Action decide(Enemy self, CombatState combat) {
        boolean low = self.hp() * 10 < self.maxHp() * 3;   // hp < 30% without floating point
        if (low && !hasDefended) {
            hasDefended = true;
            return Action.of(ActionType.DEFEND);
        }
        return Action.of(ActionType.ATTACK);
    }

    @Override
    public boolean memory() {
        return hasDefended;
    }

    @Override
    public void restoreMemory(boolean memory) {
        this.hasDefended = memory;
    }
}
