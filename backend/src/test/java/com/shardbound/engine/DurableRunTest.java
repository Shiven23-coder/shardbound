package com.shardbound.engine;

import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.exception.RunConflictException;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.view.SessionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durable runs (P-22) without a database: a game rebuilt from its stored
 * snapshot must continue exactly like the original, even mid-fight.
 */
class DurableRunTest {

    private final MutableClock clock = new MutableClock();

    private GameEngine engineWith(InMemoryRunStore runs) {
        return TestEngines.withRuns(runs, clock);
    }

    /** Plays the quick route until the first fight and lands one blow, so the fight is under way. */
    private GameState midFight(GameEngine engine) {
        GameState s = engine.newGame(1, false, QuickRoute.SEED);
        RoutePlayer player = new RoutePlayer(engine, s, clock);
        int fightStarts = QuickRoute.STEPS.indexOf(new QuickRoute.Choose("Step into the lantern light"));
        player.play(QuickRoute.STEPS.subList(0, fightStarts));
        SessionView view = player.view();
        int choice = view.dialogue().choices().getFirst().id();
        view = engine.choose(s, choice);
        assertEquals(Mode.COMBAT, view.mode());
        view = engine.combatAction(s, Action.of(ActionType.ATTACK));
        assertEquals(Mode.COMBAT, view.mode(), "the fight should still be going after one round");
        assertTrue(view.combat().round() >= 1);
        return s;
    }

    @Test
    void aResumedFightContinuesExactlyLikeTheOriginal() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine original = engineWith(store);
        GameState live = midFight(original);

        GameEngine restarted = engineWith(store.copy());           // a "new server" reading the same data
        GameState resumed = restarted.resume(live.sessionId()).orElseThrow();
        assertEquals(original.view(live), restarted.view(resumed));

        for (int round = 0; round < 3; round++) {
            SessionView a = original.combatAction(live, Action.of(ActionType.ATTACK));
            SessionView b = restarted.combatAction(resumed, Action.of(ActionType.ATTACK));
            assertEquals(a, b, "round " + round + " after the restart");
            if (a.mode() != Mode.COMBAT) {
                break;
            }
        }
    }

    @Test
    void aResumedRunKeepsThePlayersPosition() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine engine = engineWith(store);
        GameState s = engine.newGame(1, false, 7L);
        RoutePlayer player = new RoutePlayer(engine, s, clock);
        player.choose("Ignore");
        SessionView inRuins = player.exit("to_ruins");
        GameState resumed = engineWith(store.copy()).resume(s.sessionId()).orElseThrow();
        assertEquals(inRuins.world(), engine.view(resumed).world());
        assertEquals(Mode.WORLD, resumed.mode());
    }

    @Test
    void everyActionBumpsTheStoredVersion() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine engine = engineWith(store);
        GameState s = engine.newGame(1, false, 7L);
        long first = s.runVersion();
        engine.choose(s, engine.view(s).dialogue().choices().getFirst().id());
        assertEquals(first + 1, s.runVersion());
        assertEquals(s.runVersion(), store.find(s.sessionId()).orElseThrow().version());
    }

    @Test
    void aStaleCopyCannotOverwriteANewerRun() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine engine = engineWith(store);
        GameState s = engine.newGame(1, false, 7L);
        GameState stale = engine.resume(s.sessionId()).orElseThrow();   // e.g. a second server's cache

        engine.choose(s, engine.view(s).dialogue().choices().getFirst().id());   // the first copy moves on
        int choice = engine.view(stale).dialogue().choices().getFirst().id();
        assertThrows(RunConflictException.class, () -> engine.choose(stale, choice));
    }

    @Test
    void unknownSessionsDoNotResume() {
        GameEngine engine = engineWith(new InMemoryRunStore());
        assertTrue(engine.resume("no-such-session").isEmpty());
    }

    @Test
    void aFinishedRunKeepsItsEnding() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine engine = engineWith(store);
        GameState s = engine.newGame(1, true, QuickRoute.SEED);
        SessionView end = new RoutePlayer(engine, s, clock).play(QuickRoute.STEPS);
        GameState resumed = engineWith(store.copy()).resume(s.sessionId()).orElseThrow();
        SessionView again = engine.view(resumed);
        assertEquals(Mode.ENDED, again.mode());
        assertNotNull(again.ending());
        assertEquals(end.ending(), again.ending());
    }
}
