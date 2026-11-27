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
import com.shardbound.view.WeaponView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        SessionView.ItemView flask = v.inventory().items().stream().filter(i -> i.id().equals("lantern_draught"))
                .findFirst().orElseThrow();
        assertEquals(3, flask.qty(), "the Lantern Draught starts full (P-63)");
        assertEquals(3, flask.maxQty());
    }

    @Test
    void weaponViewsCarryTheServersAttackRatingCritAndGrades() {
        SessionView v = newPlayer(false, 1L).view();
        WeaponView equipped = v.hud().weapon();
        assertEquals(20, equipped.attack(), "Rusty Shortsword: base 20 × F 1.00, no scaling at 10 (P-65)");
        assertEquals(5, equipped.critPct());
        assertEquals("D", equipped.str());
        assertEquals("D", equipped.dex());
        assertNull(equipped.arc());
        SessionView.ItemView sword = v.inventory().items().stream().filter(i -> i.type().equals("WEAPON"))
                .findFirst().orElseThrow();
        assertEquals(equipped, sword.weapon(), "the Bag shows the same server numbers as the HUD");
        assertNull(v.inventory().items().stream().filter(i -> i.id().equals("lantern_draught"))
                .findFirst().orElseThrow().weapon());
    }

    @Test
    void theWeaponLadderListsEveryWeaponBestFirstRatedForANewPlayer() {
        List<WeaponView> ladder = engine.weaponLadder();
        assertEquals(List.of("SSS", "SS", "S", "A", "B", "C", "D", "E", "F"), ladder.stream().map(WeaponView::tier).toList());
        WeaponView sword = ladder.getLast();
        assertEquals("rusty_shortsword", sword.id());
        assertEquals(20, sword.attack(), "the same number a new player's Bag shows");
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

    /** "The Ash Falls" (P-67): the routes that ended in V3's cult raid now send the player to the Herald. */
    private static void expectTheAshFalls(RoutePlayer p, SessionView fall) {
        assertEquals(Mode.DIALOGUE, fall.mode());
        assertEquals("The Ash Falls", fall.dialogue().title());
        SessionView after = p.choose("Go to the ruins");
        assertEquals(Mode.WORLD, after.mode());
        assertEquals(1, p.state().flag("herald_risen"));
        assertEquals("Face the Herald in the rubble north of the ruins.", after.objective());
    }

    @Test
    void elderPathLeadsToTheAshFallsAndTheHerald() {               // P-67, replacing D3's raid
        RoutePlayer p = newPlayer(false, 1L);
        toTheSquare(p, "Ignore");
        p.interact("maren");
        p.choose("Follow her inside");
        expectTheAshFalls(p, p.choose("Promise"));
    }

    @Test
    void sellingTheShardAndGoingHomeAlsoLeadsToTheHerald() {
        RoutePlayer p = newPlayer(false, 1L);
        toTheSquare(p, "Ignore");
        p.interact("corvin_stall");
        p.choose("Accept the three hundred gold");
        assertEquals(0, p.state().inventory().count("heartshard"), "sold");
        expectTheAshFalls(p, p.choose("Go home and pretend you saw nothing"));
    }

    @Test
    void theAshFallsOpensTheRubbleNorthOfTheCrater() {
        RoutePlayer p = newPlayer(false, 1L);
        toTheSquare(p, "Ignore");
        p.interact("maren");
        p.choose("Follow her inside");
        p.choose("Promise");
        p.choose("Go to the ruins");
        p.exit("to_lane");
        SessionView ruins = p.exit("to_ruins");
        assertTrue(ruins.world().hiddenObjects().contains("ruins_rubble"), "the way north is open");
        assertEquals("eldmere_ash_rise", p.exit("arena_way").world().areaId());
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
