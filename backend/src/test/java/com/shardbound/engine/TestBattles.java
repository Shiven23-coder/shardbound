package com.shardbound.engine;

import com.shardbound.battle.BattleState;

/** Test helper: puts a battle on a state (the setter is package-private, D30). */
public final class TestBattles {

    private TestBattles() {
    }

    public static void begin(GameState state, BattleState battle) {
        state.setBattle(battle);
        state.setMode(Mode.BATTLE);
    }
}
