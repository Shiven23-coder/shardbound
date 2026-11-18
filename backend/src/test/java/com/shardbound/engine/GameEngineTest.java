package com.shardbound.engine;

import com.shardbound.api.dto.ChoiceView;
import com.shardbound.api.dto.GameView;
import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.InvalidChoiceException;
import com.shardbound.exception.LockedChoiceException;
import com.shardbound.model.Faction;
import com.shardbound.tools.ConsoleRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole-engine tests on the REAL prologue, offline (content from JSON, no DB).
 * Startup computes the quest order with QuestManager.topoOrder (Kahn).
 */
class GameEngineTest {

    private static GameEngine engine;

    @BeforeAll
    static void setUp() {
        engine = ConsoleRunner.offlineEngine(new File("../content"));
    }

    private static GameView pick(GameEngine e, GameState s, String label) {
        GameView view = e.view(s);
        for (ChoiceView c : view.choices()) {
            if (c.label().startsWith(label)) {
                return e.choose(s, c.id());
            }
        }
        throw new AssertionError("No choice starting with '" + label + "' on " + view.node().id());
    }

    @Test
    void quickRouteIsPlayableWithRealConditions() {             // spec §8.4 --auto-quick
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertTrue(ConsoleRunner.playQuickRoute(engine, new PrintStream(log)), log.toString());
    }

    @Test
    void newGameStartsAtP01WithTheRustySword() {
        GameState s = engine.newGame(0, false, 1L);
        GameView v = engine.view(s);
        assertEquals("p01", v.node().id());
        assertEquals("rusty_shortsword", v.hud().weapon().id());
        assertEquals(40, v.hud().hp());
        assertEquals("VILLAGE", v.region());
    }

    @Test
    void lockedChoiceShowsReasonAndIsRejected() {
        GameState s = engine.newGame(0, false, 1L);
        pick(engine, s, "Ignore the shouting");
        GameView square = pick(engine, s, "Take the crystal");
        ChoiceView wardens = square.choices().stream().filter(ChoiceView::locked).findFirst().orElseThrow();
        assertEquals("Requires flag met_wardens = 1", wardens.lockReason());
        assertThrows(LockedChoiceException.class, () -> engine.choose(s, wardens.id()));
    }

    @Test
    void unknownChoiceIsRejected() {
        GameState s = engine.newGame(0, false, 1L);
        assertThrows(InvalidChoiceException.class, () -> engine.choose(s, 99_999));
    }

    @Test
    void affinityUnlocksTheBetterDealAndCountsForSilverTongue() {
        GameState s = engine.newGame(0, false, 1L);
        pick(engine, s, "Help Corvin");                          // +10 affinity
        pick(engine, s, "Take the crystal");
        pick(engine, s, "Show it to Corvin");
        pick(engine, s, "\"You owe me");                          // AFFINITY corvin ≥ 10
        assertEquals(15 + 500, s.stats().gold());
        assertEquals(1, s.flag("_locked_passed"));
        assertEquals(10, s.reputation(Faction.ASHEN));
    }

    @Test
    void elderPathFoldsIntoTheRaidAndResolvesToTheWanderer() {  // D3, D11
        GameState s = engine.newGame(0, false, 1L);
        pick(engine, s, "Ignore");
        pick(engine, s, "Take the crystal");
        pick(engine, s, "Take it to Elder Maren");
        pick(engine, s, "Promise");
        GameView end = pick(engine, s, "Stand your ground");
        assertEquals("ENDED", end.mode());
        assertNotNull(end.ending());
        assertEquals("END_WANDERER", end.ending().id());
        assertEquals(6, end.ending().endingsTotal());
    }

    @Test
    void combatRequestsOutsideCombatAreRefused() {              // D27
        GameState s = engine.newGame(0, false, 1L);
        assertThrows(ActionNotAllowedException.class, () -> engine.combatAction(s, Action.of(ActionType.ATTACK)));
    }

    @Test
    void savingOfflineIsRefusedNotCrashed() {
        GameState s = engine.newGame(0, false, 1L);
        assertThrows(ActionNotAllowedException.class, () -> engine.save(s, 1));
    }

    @Test
    void toastsAreDeliveredOnce() {
        GameState s = engine.newGame(0, false, 1L);
        pick(engine, s, "Ignore");
        GameView v = pick(engine, s, "Take the crystal");
        assertTrue(v.toasts().stream().anyMatch(t -> t.text().equals("Obtained: Heartshard")));
        assertTrue(v.toasts().stream().anyMatch(t -> t.kind().equals("ACHIEVEMENT")));
        assertTrue(engine.view(s).toasts().isEmpty());
    }

    @Test
    void questLogShowsTheActiveMainQuest() {
        GameState s = engine.newGame(0, false, 1L);
        pick(engine, s, "Ignore");
        GameView v = pick(engine, s, "Take the crystal");
        assertTrue(v.quests().stream().anyMatch(q -> q.id().equals("q_strange_light") && q.status().equals("ACTIVE")));
    }
}
