package com.shardbound.engine;

import com.shardbound.battle.BattleAction;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.InvalidChoiceException;
import com.shardbound.exception.LockedChoiceException;
import com.shardbound.model.Faction;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.view.ChoiceView;
import com.shardbound.view.SessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole-engine tests on the REAL prologue in the world, in memory (content
 * from JSON and the maps, no database).
 */
class GameEngineTest {

    private final MutableClock clock = new MutableClock();
    private GameEngine engine;

    @BeforeEach
    void setUp() {
        engine = TestEngines.inMemory(clock);
    }

    private RoutePlayer newPlayer(boolean demoMode, long seed) {
        return new RoutePlayer(engine, engine.newGame(0, demoMode, seed), clock);
    }

    /** From the opening to the Square at dawn, carrying the Heartshard. */
    private static void toTheSquare(RoutePlayer p, String opening) {
        p.choose(opening);
        p.exit("to_ruins");
        p.interact("crater_crystal");
        p.choose("Take the crystal");
        p.choose("Walk on");
        p.exit("to_lane");
        p.exit("to_square");
    }

    @Test
    void quickRouteIsPlayableWithRealConditions() {
        RoutePlayer p = newPlayer(true, QuickRoute.SEED);
        SessionView end = p.play(QuickRoute.STEPS);
        assertEquals(Mode.ENDED, end.mode());
        assertNotNull(end.ending());
        assertEquals("END_QUICK", end.ending().id());
    }

    @Test
    void aNewGameOpensOnTheStormNightAtHome() {
        SessionView v = newPlayer(false, 1L).view();
        assertEquals(Mode.DIALOGUE, v.mode());
        assertEquals("p01", v.dialogue().nodeId());
        assertEquals("eldmere_cottage_lane", v.world().areaId());
        assertEquals("eldmere", v.world().regionId());
        assertEquals("storm_night", v.world().lightState());
        assertEquals(7 * 16 + 8, v.world().player().x());           // the centre of the home spawn
        assertEquals(10 * 16 + 8, v.world().player().y());
        assertEquals("rusty_shortsword", v.hud().weapon().id());
        assertEquals(100, v.hud().hp());                            // V2 stats: every attribute at 10 (P-55)
        assertEquals(30, v.hud().fp());
        assertEquals(1, v.hud().level());
        assertEquals(3, v.inventory().items().stream().filter(i -> i.id().equals("healing_draught"))
                .findFirst().orElseThrow().qty(), "the starting kit's draughts");
    }

    @Test
    void endingADialogueReturnsToTheWorld() {
        RoutePlayer p = newPlayer(false, 1L);
        SessionView v = p.choose("Ignore the shouting");
        assertEquals(Mode.WORLD, v.mode());
        assertNull(v.dialogue());
        assertTrue(v.world().npcs().stream().anyMatch(n -> n.npcId().equals("corvin")), "Corvin is on the lane");
    }

    @Test
    void lockedChoiceShowsReasonAndIsRejected() {
        RoutePlayer p = newPlayer(false, 1L);
        p.choose("Ignore");
        SessionView sign = p.interact("forest_sign");
        ChoiceView wardens = sign.dialogue().choices().stream().filter(ChoiceView::locked).findFirst().orElseThrow();
        assertTrue(wardens.lockReason().startsWith("Requires flag met_wardens = 1"), wardens.lockReason());
        assertThrows(LockedChoiceException.class, () -> engine.choose(p.state(), wardens.id()));
    }

    @Test
    void unknownChoiceIsRejected() {
        GameState s = engine.newGame(0, false, 1L);
        assertThrows(InvalidChoiceException.class, () -> engine.choose(s, 99_999));
    }

    @Test
    void affinityUnlocksTheBetterDealAndCountsForSilverTongue() {
        RoutePlayer p = newPlayer(false, 1L);
        toTheSquare(p, "Help Corvin");                           // +10 affinity
        p.interact("corvin_stall");
        p.choose("\"You owe me");                                // AFFINITY corvin ≥ 10
        GameState s = p.state();
        assertEquals(15 + 500, s.stats().gold());
        assertEquals(1, s.flag("_locked_passed"));
        assertEquals(10, s.reputation(Faction.ASHEN));
    }

    @Test
    void elderPathFoldsIntoTheRaidAndResolvesToTheWanderer() {  // D3, D11
        RoutePlayer p = newPlayer(false, 1L);
        toTheSquare(p, "Ignore");
        p.interact("maren");
        p.choose("Follow her inside");
        p.choose("Promise");
        SessionView end = p.choose("Stand your ground");
        assertEquals(Mode.ENDED, end.mode());
        assertNotNull(end.ending());
        assertEquals("END_WANDERER", end.ending().id());
        assertEquals(6, end.ending().endingsTotal());
    }

    @Test
    void takingTheCrystalTurnsTheNightToDawn() {
        RoutePlayer p = newPlayer(false, 1L);
        p.choose("Ignore");
        p.exit("to_ruins");
        p.interact("crater_crystal");
        SessionView v = p.choose("Take the crystal");
        assertEquals("dawn", v.world().lightState());
        assertTrue(v.toasts().stream().anyMatch(t -> t.text().equals("Obtained: Heartshard")));
        assertTrue(v.toasts().stream().anyMatch(t -> t.kind().equals("ACHIEVEMENT")));
        assertTrue(v.quests().stream().anyMatch(q -> q.id().equals("q_strange_light") && q.status().equals("ACTIVE")));
        assertTrue(engine.view(p.state()).toasts().isEmpty(), "toasts are delivered once");
    }

    @Test
    void battleRequestsOutsideBattleAreRefused() {              // D27
        GameState s = engine.newGame(0, false, 1L);
        assertThrows(ActionNotAllowedException.class,
                () -> engine.battleAction(s, BattleAction.of(BattleAction.Kind.STRIKE)));
    }

    @Test
    void savingOfflineIsRefusedNotCrashed() {
        RoutePlayer p = newPlayer(false, 1L);
        p.choose("Ignore");
        assertThrows(ActionNotAllowedException.class, () -> engine.save(p.state(), 1));
    }
}
