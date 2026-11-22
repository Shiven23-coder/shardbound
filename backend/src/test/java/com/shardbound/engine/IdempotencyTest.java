package com.shardbound.engine;

import com.shardbound.exception.IdempotencyKeyReusedException;
import com.shardbound.support.MutableClock;
import com.shardbound.view.SessionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Idempotency keys (P-31, P-56): an action is applied once per key, and the key survives a restart. */
class IdempotencyTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void aRepeatedKeyAppliesNothingAndReturnsTheCurrentView() {
        GameEngine engine = TestEngines.inMemory(clock);
        GameState s = engine.newGame(0, false, 1L);
        ActionKey key = new ActionKey("key-0001", "use draught");
        engine.once(s, key, () -> engine.use(s, "healing_draught"));
        int left = s.inventory().count("healing_draught");
        SessionView repeat = engine.once(s, key, () -> engine.use(s, "healing_draught"));
        assertEquals(left, s.inventory().count("healing_draught"), "the second request applied nothing");
        assertEquals(engine.view(s).hud(), repeat.hud());
    }

    @Test
    void aKeyReusedForADifferentRequestIsRefused() {
        GameEngine engine = TestEngines.inMemory(clock);
        GameState s = engine.newGame(0, false, 1L);
        engine.once(s, new ActionKey("key-0002", "use draught"), () -> engine.use(s, "healing_draught"));
        assertThrows(IdempotencyKeyReusedException.class,
                () -> engine.once(s, new ActionKey("key-0002", "equip"), () -> engine.equip(s, "rusty_shortsword")));
    }

    @Test
    void onlyTheLastEightActionsAreRemembered() {
        GameEngine engine = TestEngines.inMemory(clock);
        GameState s = engine.newGame(0, false, 1L);
        for (int i = 0; i < RecentActions.CAPACITY + 2; i++) {
            engine.once(s, new ActionKey("key-x" + i, "equip"), () -> engine.equip(s, "rusty_shortsword"));
        }
        assertEquals(RecentActions.CAPACITY, s.recentActions().entries().size());
        assertEquals(RecentActions.Match.NEW, s.recentActions().match(new ActionKey("key-x0", "equip")));
    }

    @Test
    void aKeyIsRememberedInTheSameWriteAndSurvivesARestart() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine engine = TestEngines.withRuns(store, clock);
        GameState s = engine.newGame(0, false, 1L);
        ActionKey key = new ActionKey("key-0003", "use draught");
        engine.once(s, key, () -> engine.use(s, "healing_draught"));
        int left = s.inventory().count("healing_draught");

        GameEngine restarted = TestEngines.withRuns(store.copy(), clock);
        GameState resumed = restarted.resume(s.sessionId()).orElseThrow();
        assertEquals(s.runVersion(), resumed.recentActions().entries().getLast().runVersion());
        restarted.once(resumed, key, () -> restarted.use(resumed, "healing_draught"));
        assertEquals(left, resumed.inventory().count("healing_draught"), "the retry after a restart applied nothing");
    }
}
