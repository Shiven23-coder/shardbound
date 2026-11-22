package com.shardbound.engine;

import com.shardbound.combat.CombatState;

/** Test-only bridge to GameState's package-private combat setter. */
public final class TestCombat {

    private TestCombat() {
    }

    public static void setCombat(GameState state, CombatState combat) {
        state.setCombat(combat);
        state.setMode(combat == null ? Mode.DIALOGUE : Mode.COMBAT);
    }
}
