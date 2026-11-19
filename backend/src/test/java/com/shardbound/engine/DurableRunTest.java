package com.shardbound.engine;

import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.exception.RunConflictException;
import com.shardbound.view.ChoiceView;
import com.shardbound.view.GameView;
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

    private static GameEngine engineWith(InMemoryRunStore runs) {
        return new GameEngine(TestEngines.content(), null, null, runs);
    }

    /** Plays the quick route until the first fight and lands one blow, so the fight is under way. */
    private static GameState midFight(GameEngine engine) {
        GameState s = engine.newGame(1, false, TestEngines.QUICK_ROUTE_SEED);
        GameView view = engine.view(s);
        while (view.mode() != Mode.COMBAT) {
            int index = TestEngines.QUICK_ROUTE.indexOf(view.node().id());
            String next = TestEngines.QUICK_ROUTE.get(index + 1);
            view = chooseTowards(engine, s, view, next);
        }
        view = engine.combatAction(s, Action.of(ActionType.ATTACK));
        assertEquals(Mode.COMBAT, view.mode(), "the fight should still be going after one round");
        assertTrue(view.combat().round() >= 1);
        return s;
    }

    private static GameView chooseTowards(GameEngine engine, GameState s, GameView view, String target) {
        for (ChoiceView choice : view.choices()) {
            if (!choice.locked() && engine.content().graph().choice(view.node().id(), choice.id())
                    .map(c -> c.to().equals(target)).orElse(false)) {
                return engine.choose(s, choice.id());
            }
        }
        throw new AssertionError("No way from " + view.node().id() + " to " + target);
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
            GameView a = original.combatAction(live, Action.of(ActionType.ATTACK));
            GameView b = restarted.combatAction(resumed, Action.of(ActionType.ATTACK));
            assertEquals(a, b, "round " + round + " after the restart");
            if (a.mode() != Mode.COMBAT) {
                break;
            }
        }
    }

    @Test
    void everyActionBumpsTheStoredVersion() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine engine = engineWith(store);
        GameState s = engine.newGame(1, false, 7L);
        long first = s.runVersion();
        GameView view = engine.view(s);
        engine.choose(s, view.choices().get(0).id());
        assertEquals(first + 1, s.runVersion());
        assertEquals(s.runVersion(), store.find(s.sessionId()).orElseThrow().version());
    }

    @Test
    void aStaleCopyCannotOverwriteANewerRun() {
        InMemoryRunStore store = new InMemoryRunStore();
        GameEngine engine = engineWith(store);
        GameState s = engine.newGame(1, false, 7L);
        GameState stale = engine.resume(s.sessionId()).orElseThrow();   // e.g. a second server's cache

        engine.choose(s, engine.view(s).choices().get(0).id());          // the first copy moves on
        int choice = engine.view(stale).choices().get(0).id();
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
        GameState s = engine.newGame(1, true, TestEngines.QUICK_ROUTE_SEED);
        GameView end = TestEngines.playQuickRoute(engine, s);
        GameState resumed = engineWith(store.copy()).resume(s.sessionId()).orElseThrow();
        GameView again = engine.view(resumed);
        assertEquals(Mode.ENDED, again.mode());
        assertNotNull(again.ending());
        assertEquals(end.ending(), again.ending());
    }
}
