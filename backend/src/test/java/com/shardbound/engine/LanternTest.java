package com.shardbound.engine;

import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.progression.Attribute;
import com.shardbound.support.MutableClock;
import com.shardbound.view.LanternMenuView;
import com.shardbound.view.LevelPreviewView;
import com.shardbound.view.SessionView;
import com.shardbound.world.Lantern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Junction Lantern (V2_SPEC §4.1, §4.3, P-61): cold, kindled, rested at, levelled at. */
class LanternTest {

    private static final String LANTERN = "junction_lantern";
    private static final String LIT = Lantern.litFlag("junction");

    private final MutableClock clock = new MutableClock();
    private GameEngine engine;
    private RoutePlayer player;

    @BeforeEach
    void stormNightInTheLane() {
        engine = TestEngines.inMemory(clock);
        player = new RoutePlayer(engine, engine.newGame(0, false, 3L), clock);
        player.choose("Help Corvin lash down his wagon");
    }

    private GameState state() {
        return player.state();
    }

    /** Takes the Heartshard from the ruins and comes back to the lane. */
    private void fetchTheHeartshard() {
        player.exit("to_ruins");
        player.interact("crater_crystal");
        player.choose("Take the crystal in your bare hands");
        player.choose("Walk on");
        player.exit("to_lane");
    }

    private SessionView kindleAndRest() {
        fetchTheHeartshard();
        player.interact(LANTERN);
        player.choose("Warm your hands");
        return player.interact(LANTERN);
    }

    @Test
    void aColdLanternWithoutTheHeartshardOnlyShowsAMessage() {
        SessionView v = player.interact(LANTERN);
        assertEquals(Mode.DIALOGUE, v.mode());
        assertTrue(v.dialogue().message(), "the cold glass is a message box");
        assertEquals(0, state().flag(LIT));
        assertEquals(LANTERN, v.world().lanterns().getFirst().id());
        assertFalse(v.world().lanterns().getFirst().lit());
    }

    @Test
    void theHeartshardKindlesItOnceAndThenTheLanternIsRestedAt() {
        fetchTheHeartshard();
        SessionView kindle = player.interact(LANTERN);
        assertEquals("junction_kindle", kindle.dialogue().nodeId());
        SessionView lit = player.choose("Warm your hands");
        assertEquals(Mode.WORLD, lit.mode());
        assertTrue(lit.world().lanterns().getFirst().lit());
        assertEquals(Mode.LANTERN, player.interact(LANTERN).mode(), "a lit Lantern rests at once");
    }

    @Test
    void restingHealsRefillsTheDraughtBringsBackTheCreepsAndOpensTheMenu() {
        fetchTheHeartshard();
        player.interact(LANTERN);
        player.choose("Warm your hands");
        state().stats().damage(60);
        state().stats().spendFp(20);
        state().setFlaskCharges("lantern_draught", 0);
        state().creeps().markDefeated(CreepRoster.key("eldmere_cottage_lane", "meadow_creep_a"));

        SessionView v = player.interact(LANTERN);
        assertEquals(Mode.LANTERN, v.mode());
        assertEquals(state().stats().maxHp(), state().stats().hp());
        assertEquals(state().stats().maxFp(), state().stats().fp());
        assertEquals(3, state().flaskCharges("lantern_draught"));
        assertFalse(state().creeps().isDefeated(CreepRoster.key("eldmere_cottage_lane", "meadow_creep_a")));
        assertEquals("junction", state().lastLantern());
        LanternMenuView menu = v.lantern();
        assertEquals("Junction Lantern", menu.name());
        assertEquals(1, menu.level());
        assertEquals(100, menu.nextLevelCost());
        assertEquals(6, menu.attributes().size());
        assertEquals(4, menu.loadout().size());
    }

    @Test
    void aLitLanternStaysUsableAfterTheHeartshardIsSoldOnTheMerchantRoute() {
        kindleAndRest();
        engine.leaveLantern(state());
        player = new RoutePlayer(engine, state(), clock);
        player.exit("to_square");
        player.interact("square_dawn");
        player.choose("Look around the square");
        player.interact("corvin_stall");
        player.choose("Accept the three hundred gold");
        player.choose("Follow him");
        assertEquals(0, state().inventory().count("heartshard"), "sold to Corvin");
        player.exit("to_lane");
        SessionView again = player.interact(LANTERN);
        assertEquals(Mode.LANTERN, again.mode(), "lit for good (P-61)");
    }

    @Test
    void levelsAreBoughtWithShardsAfterAPreview() {
        kindleAndRest();
        state().stats().addShards(400);
        LevelPreviewView preview = engine.previewLevelUp(state(), Map.of(Attribute.VIGOR, 2));
        assertEquals(1, preview.levelBefore());
        assertEquals(3, preview.levelAfter());
        assertEquals(383, preview.cost());
        assertEquals(17, preview.shardsAfter());
        assertTrue(preview.affordable());
        assertTrue(preview.after().maxHp() > preview.before().maxHp());
        assertEquals(400, state().stats().shards(), "a preview changes nothing");

        SessionView v = engine.levelUp(state(), Map.of(Attribute.VIGOR, 2));
        assertEquals(3, v.lantern().level());
        assertEquals(17, v.lantern().shards());
        assertEquals(12, v.lantern().attributes().stream().filter(a -> a.id() == Attribute.VIGOR).findFirst()
                .orElseThrow().value());
        assertTrue(v.toasts().stream().anyMatch(t -> t.text().equals("Level 3")));
    }

    @Test
    void anUnaffordableOrEmptyLevelUpIsRefused() {
        kindleAndRest();
        assertThrows(ActionNotAllowedException.class, () -> engine.levelUp(state(), Map.of(Attribute.MIND, 1)));
        state().stats().addShards(1000);
        assertThrows(ActionNotAllowedException.class, () -> engine.levelUp(state(), Map.of()));
        assertThrows(ActionNotAllowedException.class, () -> engine.levelUp(state(), Map.of(Attribute.MIND, -1)));
    }

    @Test
    void levellingAndTheLoadoutHappenOnlyAtALantern() {
        assertThrows(ActionNotAllowedException.class,
                () -> engine.previewLevelUp(state(), Map.of(Attribute.VIGOR, 1)));
        assertThrows(ActionNotAllowedException.class,
                () -> engine.setLoadout(state(), Arrays.asList(null, null, null, null)));
    }

    @Test
    void theLoadoutTakesKnownSkillsOnceEach() {
        kindleAndRest();
        SessionView v = engine.setLoadout(state(), Arrays.asList("crystal_pulse", null, "rending_cut", null));
        assertEquals("crystal_pulse", v.lantern().loadout().get(0).skillId());
        assertNull(v.lantern().loadout().get(1).skillId());
        assertThrows(ActionNotAllowedException.class,
                () -> engine.setLoadout(state(), Arrays.asList("ember", null, null, null)), "not known yet");
        assertThrows(ActionNotAllowedException.class,
                () -> engine.setLoadout(state(), Arrays.asList("rending_cut", "rending_cut", null, null)));
        assertThrows(ActionNotAllowedException.class,
                () -> engine.setLoadout(state(), Arrays.asList("rending_cut", null, null)));
    }

    @Test
    void leavingReturnsToTheWorld() {
        kindleAndRest();
        SessionView v = engine.leaveLantern(state());
        assertEquals(Mode.WORLD, v.mode());
        assertNull(v.lantern());
        assertThrows(ActionNotAllowedException.class, () -> engine.leaveLantern(state()));
    }
}
