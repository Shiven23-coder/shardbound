package com.shardbound.engine;

import com.shardbound.battle.BattleEngine;
import com.shardbound.battle.BattleState;
import com.shardbound.support.MutableClock;
import com.shardbound.view.BattleView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Teaching "Guard the fall" (P-67): while a creep's telegraphed blow is pending the battle view carries the
 * warning and the Guard option's subtitle from balance.json (the Herald's own: HeraldTest).
 */
class BraceCueTest {

    private final MutableClock clock = new MutableClock();
    private final GameEngine engine = TestEngines.inMemory(clock);

    @Test
    void aCreepsPendingTelegraphCarriesTheSharedWarningAndGuardSubtitle() {
        GameState s = engine.newGame(0, false, 3L);
        BattleEngine battles = new BattleEngine(engine.content().battle(), engine.content().items());
        BattleState battle = battles.start(s, engine.content().battle().creeps().get("storm_rat"), 2, null, "test", true);
        TestBattles.begin(s, battle);
        assertNull(engine.view(s).battle().brace(), "nothing pending, nothing to brace for");

        battle.enemy().announce(engine.content().battle().skills().get("rat_frenzy"));
        BattleView.BraceView brace = engine.view(s).battle().brace();
        assertEquals("Frenzy next turn — Guard to brace", brace.warning());
        assertEquals("Brace against the blow", brace.guardDetail());
    }
}
