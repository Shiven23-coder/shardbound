package com.shardbound.engine;

import com.shardbound.battle.BattleAction;
import com.shardbound.battle.BattleOutcome;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.support.MutableClock;
import com.shardbound.view.CutsceneView;
import com.shardbound.view.SessionView;
import com.shardbound.world.AreaMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Ashen Herald in play (P-67, P-68): the intro on the first step onto the Rise, a keyed finish into the
 * fight, a death that wakes the player at the Ruins Lantern, a retry without the intro, and a victory that
 * plays the epilogue and leaves Eldmere changed.
 */
class HeraldTest {

    private static final String RISE = "eldmere_ash_rise";
    private static final String HOLDING = "Little lamp-bearer. You carry what was ours.";
    private static final String SOLD = "Little lamp-bearer. You carried what was ours. The stink of it is on you still.";

    private final MutableClock clock = new MutableClock();
    private GameEngine engine;
    private RoutePlayer player;

    @BeforeEach
    void newGame() {
        engine = TestEngines.inMemory(clock);
        player = new RoutePlayer(engine, engine.newGame(0, false, 3L), clock);
    }

    private GameState state() {
        return player.state();
    }

    /** The storm night, the crystal, the square; then Maren (keeping the shard) or Corvin's gold (selling it). */
    private void toTheAshFalls(boolean sell) {
        player.choose("Ignore");
        player.exit("to_ruins");
        player.interact("crater_crystal");
        player.choose("Take the crystal");
        player.choose("Walk on");
        player.exit("to_lane");
        player.exit("to_square");
        if (sell) {
            player.interact("corvin_stall");
            player.choose("Accept the three hundred gold");
            player.choose("Go home and pretend you saw nothing");
        } else {
            player.interact("maren");
            player.choose("Follow her inside");
            player.choose("Promise");
        }
        player.choose("Go to the ruins");
    }

    /** Back to the ruins, the Ruins Lantern kindled and rested at, then up onto the Rise. */
    private void toTheRise(boolean sell) {
        toTheAshFalls(sell);
        player.exit("to_lane");
        player.exit("to_ruins");
        player.interact("ruins_lantern");
        player.choose("Warm your hands");
        player.interact("ruins_lantern");                         // rests: the menu opens
        engine.leaveLantern(state());
        player.exit("arena_way");
        assertEquals(RISE, state().position().areaId());
    }

    /** Walks onto the scorched floor, where the trigger lies across the way. */
    private SessionView stepOnTheTrigger() {
        AreaMap rise = engine.content().world().mapOf(RISE);
        clock.advance(RoutePlayer.WALK);
        return engine.interact(state(), "herald_trigger", rise.centre(10), rise.centre(7), state().position().facing());
    }

    private static String firstLine(SessionView v) {
        return v.cutscene().steps().stream().filter(s -> s.kind().equals("SAY")).findFirst().orElseThrow().text();
    }

    @Test
    void theFirstStepOntoTheRisePlaysTheIntroToAPlayerHoldingTheShard() {
        toTheRise(false);
        SessionView intro = stepOnTheTrigger();
        assertEquals(Mode.CUTSCENE, intro.mode());
        CutsceneView cutscene = intro.cutscene();
        assertEquals("herald_intro", cutscene.id());
        assertEquals(HOLDING, firstLine(intro));
        assertTrue(intro.world().bosses().isEmpty(), "the Herald arrives in the intro");
    }

    @Test
    void aPlayerWhoSoldTheShardIsToldSo() {
        toTheRise(true);
        assertEquals(SOLD, firstLine(stepOnTheTrigger()));
    }

    @Test
    void finishingTheIntroStartsTheFightThatCannotBeFled() {
        toTheRise(false);
        stepOnTheTrigger();
        SessionView fight = engine.finishCutscene(state(), "herald_intro");
        assertEquals(Mode.BATTLE, fight.mode());
        assertEquals("Ashen Herald", fight.battle().boss().name());
        assertEquals(1, fight.battle().boss().phase());
        assertEquals(50, fight.battle().boss().phase2Pct());
        assertFalse(fight.battle().menu().canFlee());
        assertEquals(1, state().flag(GameEngine.seenFlag("herald_intro")));
    }

    @Test
    void onlyTheCutscenePlayingCanBeFinished() {
        toTheRise(false);
        assertThrows(ActionNotAllowedException.class, () -> engine.finishCutscene(state(), "herald_intro"));
        stepOnTheTrigger();
        assertThrows(ActionNotAllowedException.class, () -> engine.finishCutscene(state(), "herald_epilogue"));
    }

    @Test
    void aDeathWakesThePlayerAtTheRuinsLanternAndTheRetrySkipsTheIntro() {
        toTheRise(false);
        stepOnTheTrigger();
        engine.finishCutscene(state(), "herald_intro");
        SessionView lost = null;
        while (state().mode() == Mode.BATTLE) {
            lost = engine.battleAction(state(), BattleAction.of(BattleAction.Kind.GUARD));
        }
        assertEquals("LOST", lost.battle().outcome());
        assertEquals("ruins", state().lastLantern());
        assertEquals("eldmere_old_ruins", state().position().areaId(), "woken at the Ruins Lantern");

        player.exit("arena_way");
        SessionView retry = stepOnTheTrigger();
        assertEquals(Mode.BATTLE, retry.mode(), "no intro the second time: straight to the fight and its banner");
        assertNull(retry.cutscene());
        assertEquals(1, retry.world().bosses().size(), "the Herald waits on the rise");
    }

    @Test
    void victoryPlaysTheEpilogueThenEldmereIsChanged() {
        toTheRise(false);
        stepOnTheTrigger();
        engine.finishCutscene(state(), "herald_intro");
        state().battle().enemy().restore(1, false, List.of());
        SessionView won = engine.battleAction(state(), BattleAction.of(BattleAction.Kind.STRIKE));
        assertEquals(BattleOutcome.WON.name(), won.battle().outcome());
        assertEquals(Mode.CUTSCENE, won.mode());
        assertEquals("herald_epilogue", won.cutscene().id());
        assertEquals(1, state().flag("herald_defeated"));
        assertTrue(state().inventory().has("iron_longsword"));

        SessionView after = engine.finishCutscene(state(), "herald_epilogue");
        assertEquals(Mode.WORLD, after.mode());
        assertEquals("ash_lifted", after.world().lightState());
        assertTrue(after.world().bosses().isEmpty(), "the Rise is quiet");
        assertTrue(after.world().hiddenObjects().contains("herald_trigger"));
        assertEquals("The road west is drying. Rest, and make ready.", after.objective());
    }

    @Test
    void aRunStoredMidCutsceneResumesIntoTheSameCutscene() {
        toTheRise(false);
        stepOnTheTrigger();
        GameSnapshots snapshots = new GameSnapshots(engine.content(), new com.shardbound.battle.BattleEngine(
                engine.content().battle(), engine.content().items()));
        GameState resumed = snapshots.restoreRun(snapshots.run(state()), state().sessionId(), state().playerId());
        assertEquals(Mode.CUTSCENE, resumed.mode());
        assertEquals("herald_intro", resumed.cutscene());
    }
}
