package com.shardbound.combat.ai;

import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.combat.CombatState;
import com.shardbound.combat.Enemy;

/**
 * Two phases (spec §6.6). Phase 1: a heavy attack (SKILL, ×1.6) on every
 * 3rd turn, normal attacks otherwise. Phase 2 starts at ≤ 50% hp and makes
 * every 2nd turn heavy. (The spec does not say what phase 2 changes; making
 * heavy attacks more frequent is this project's interpretation.)
 */
public class BossAI implements EnemyAI {

    private boolean phaseTwo;

    @Override
    public Action decide(Enemy self, CombatState combat) {
        if (!phaseTwo && self.hp() * 2 <= self.maxHp()) {
            phaseTwo = true;
        }
        int turnNumber = self.turnsTaken() + 1;          // 1-based, this turn included
        int every = phaseTwo ? 2 : 3;
        if (turnNumber % every == 0) {
            return Action.of(ActionType.SKILL);
        }
        return Action.of(ActionType.ATTACK);
    }

    public boolean isPhaseTwo() {
        return phaseTwo;
    }
}
