package com.shardbound.engine;

import com.shardbound.battle.BattleAction;
import com.shardbound.exception.WorldEventRejectedException;
import com.shardbound.support.MutableClock;
import com.shardbound.view.SessionView;
import com.shardbound.world.AreaMap;
import com.shardbound.world.Facing;
import com.shardbound.world.MapObject;
import com.shardbound.world.WorldPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dying, Lost Shards and getting them back (V2_SPEC §4.4, P-62). */
class DeathTest {

    private static final String LANE = "eldmere_cottage_lane";
    private static final String RAT = "meadow_creep_a";

    private final MutableClock clock = new MutableClock();
    private GameEngine engine;
    private GameState s;

    /** Rested at the Junction Lantern with the Heartshard, back in the lane. */
    @BeforeEach
    void restedAtTheJunction() {
        engine = TestEngines.inMemory(clock);
        RoutePlayer player = new RoutePlayer(engine, engine.newGame(0, false, 5L), clock);
        player.choose("Help Corvin lash down his wagon");
        player.exit("to_ruins");
        player.interact("crater_crystal");
        player.choose("Take the crystal in your bare hands");
        player.choose("Walk on");
        player.exit("to_lane");
        player.interact("junction_lantern");
        player.choose("Warm your hands");
        player.interact("junction_lantern");
        s = player.state();
        engine.leaveLantern(s);
    }

    private AreaMap lane() {
        return engine.content().world().mapOf(LANE);
    }

    /** Fights the Storm Rat from a tile next to it and loses. */
    private SessionView dieToTheRat() {
        MapObject rat = lane().object(RAT).orElseThrow();
        clock.advance(Duration.ofMinutes(1));
        engine.encounter(s, RAT, lane().centre(rat.x()), lane().centre(rat.y() - 1), Facing.DOWN);
        SessionView v = engine.view(s);
        while (v.mode() == Mode.BATTLE) {
            s.stats().damage(1000);
            v = engine.battleAction(s, BattleAction.of(BattleAction.Kind.GUARD));
        }
        return v;
    }

    @Test
    void heldShardsDropWhereTheBattleStartedAndThePlayerWakesAtTheLantern() {
        s.stats().addShards(150);
        SessionView v = dieToTheRat();
        MapObject rat = lane().object(RAT).orElseThrow();
        assertEquals(new LostShards(LANE, rat.x(), rat.y() - 1, 150), s.lostShards());
        assertEquals(0, s.stats().shards());
        WorldPosition rest = WorldPosition.at(LANE, lane(), lane().spawn("junction_rest").orElseThrow());
        assertEquals(rest.x(), s.position().x());
        assertEquals(rest.y(), s.position().y());
        assertEquals(3, s.flaskCharges("lantern_draught"), "the draught is refilled on death (P-63)");
        assertEquals(1, s.flag("deaths"));
        assertEquals(150, v.world().lostShards().amount());
        assertTrue(v.battle().text().contains("You wake beside the Junction Lantern."));
    }

    @Test
    void theBattlesLastLineSaysWhereThePlayerWakes() {
        SessionView v = dieToTheRat();
        java.util.List<String> text = v.battle().text();
        assertEquals("You wake beside the Junction Lantern.", text.getLast(),
                "read before Continue: a toast would be hidden behind the battle");
    }

    @Test
    void nothingHeldDropsNothing() {
        dieToTheRat();
        assertNull(s.lostShards());
    }

    @Test
    void dyingAgainBeforeRecoveringDestroysTheEarlierShards() {
        s.stats().addShards(150);
        dieToTheRat();
        s.stats().addShards(40);
        SessionView v = dieToTheRat();
        assertEquals(40, s.lostShards().amount(), "only the newest drop remains");
        assertTrue(v.battle().text().contains("The Shards you lost before fade into the dark."));
    }

    @Test
    void steppingWithinOneTileRecoversThemOnce() {
        s.stats().addShards(150);
        dieToTheRat();
        LostShards lost = s.lostShards();
        clock.advance(Duration.ofMinutes(1));
        assertThrows(WorldEventRejectedException.class, () -> engine.recover(s, lane().centre(lost.tileX() - 2),
                lane().centre(lost.tileY()), Facing.RIGHT), "two tiles away is too far");
        clock.advance(Duration.ofMinutes(1));
        SessionView v = engine.recover(s, lane().centre(lost.tileX()), lane().centre(lost.tileY() - 1), Facing.DOWN);
        assertEquals(150, s.stats().shards());
        assertNull(s.lostShards());
        assertNull(v.world().lostShards());
        assertTrue(v.toasts().stream().anyMatch(t -> t.text().equals("Shards recovered")));
        clock.advance(Duration.ofMinutes(1));
        assertThrows(WorldEventRejectedException.class, () -> engine.recover(s, lane().centre(lost.tileX()),
                lane().centre(lost.tileY() - 1), Facing.DOWN), "nothing left to pick up");
    }
}
