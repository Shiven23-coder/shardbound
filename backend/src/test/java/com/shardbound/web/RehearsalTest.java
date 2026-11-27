package com.shardbound.web;

import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.engine.Mode;
import com.shardbound.engine.RoutePlayer;
import com.shardbound.engine.TestEngines;
import com.shardbound.support.MutableClock;
import com.shardbound.view.SessionView;
import com.shardbound.world.AreaMap;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The e2e tests' Herald rehearsal (P-67) leaves the run where the elder's path does: the same story state, at
 * the Ruins Lantern, ready for the intro; stronger by the raises the route would have bought.
 */
class RehearsalTest {

    private final MutableClock clock = new MutableClock();
    private final GameEngine engine = TestEngines.inMemory(clock);

    /** The elder's path played to the Ruins Lantern, rested at and left, as HeraldTest plays it. */
    private GameState played() {
        RoutePlayer player = new RoutePlayer(engine, engine.newGame(0, false, 3L), clock);
        player.choose("Ignore");
        player.exit("to_ruins");
        player.interact("crater_crystal");
        player.choose("Take the crystal");
        player.choose("Walk on");
        player.exit("to_lane");
        player.exit("to_square");
        player.interact("maren");
        player.choose("Follow her inside");
        player.choose("Promise");
        player.choose("Go to the ruins");
        player.exit("to_lane");
        player.exit("to_ruins");
        player.interact("ruins_lantern");
        player.choose("Warm your hands");
        player.interact("ruins_lantern");
        engine.leaveLantern(player.state());
        return player.state();
    }

    private GameState rehearsed() {
        GameState s = engine.newGame(0, false, 3L);
        engine.rehearse(s, RehearsalController.READY_FOR_THE_HERALD);
        return s;
    }

    @Test
    void theRehearsalMatchesTheStoryStateOfThePlayedRoute() {
        GameState played = played();
        GameState rehearsed = rehearsed();
        assertEquals(played.flagsView(), rehearsed.flagsView());
        assertEquals(played.questsView(), rehearsed.questsView());
        assertEquals(played.reputationView(), rehearsed.reputationView());
        assertEquals(played.affinityView(), rehearsed.affinityView());
        assertEquals(played.lastLantern(), rehearsed.lastLantern());
        assertEquals(played.position().areaId(), rehearsed.position().areaId());
        assertEquals(Mode.WORLD, rehearsed.mode());
        assertTrue(rehearsed.inventory().has("heartshard"));
    }

    @Test
    void theRehearsedPlayerIsLevelFourWithTheHatchetAndEmber() {
        GameState s = rehearsed();
        assertEquals(4, s.stats().level());
        assertEquals("woodcutter_hatchet", s.inventory().equippedWeapon().id());
        assertTrue(s.knownSkills().contains("ember"));
        assertEquals(s.stats().maxHp(), s.stats().hp());
    }

    @Test
    void fromTheRehearsalTheRiseAndItsIntroAreReachable() {
        GameState s = rehearsed();
        RoutePlayer player = new RoutePlayer(engine, s, clock);
        player.exit("arena_way");
        AreaMap rise = engine.content().world().mapOf("eldmere_ash_rise");
        clock.advance(RoutePlayer.WALK);
        SessionView intro = engine.interact(s, "herald_trigger", rise.centre(10), rise.centre(6), s.position().facing());
        assertEquals(Mode.CUTSCENE, intro.mode());
        assertEquals("herald_intro", intro.cutscene().id());
    }

    @Test
    void theRehearsalExistsOnlyInTheE2eProfile() {
        assertArrayEquals(new String[] {"e2e"}, RehearsalController.class.getAnnotation(Profile.class).value());
    }
}
