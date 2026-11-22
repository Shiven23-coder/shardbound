package com.shardbound.engine;

import com.shardbound.battle.BattleAction;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.WorldEventRejectedException;
import com.shardbound.support.MutableClock;
import com.shardbound.view.SessionView;
import com.shardbound.view.WorldView;
import com.shardbound.world.AreaMap;
import com.shardbound.world.CollisionGrid;
import com.shardbound.world.CreepSpawn;
import com.shardbound.world.Facing;
import com.shardbound.world.MapObject;
import com.shardbound.world.WorldPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creeps on the map and the battles they start (V2_SPEC §4.2, P-54, P-55), on the real Eldmere maps. */
class EncounterTest {

    private static final String LANE = "eldmere_cottage_lane";
    private static final String SLIME = "meadow_creep_b";
    private static final String RAT = "meadow_creep_a";

    private final MutableClock clock = new MutableClock();
    private GameEngine engine;
    private GameState state;

    @BeforeEach
    void inTheLane() {
        engine = TestEngines.inMemory(clock);
        state = engine.newGame(0, false, 1L);
        new RoutePlayer(engine, state, clock).choose("Ignore");     // the opening ends: WORLD mode
    }

    private AreaMap lane() {
        return engine.content().world().mapOf(LANE);
    }

    private CreepSpawn creep(String name) {
        return CreepSpawn.of(lane().object(name).orElseThrow());
    }

    /** A reachable tile centre exactly {@code distance} tiles from the creep. */
    private int[] spotAt(String creepName, int distance) {
        MapObject o = lane().object(creepName).orElseThrow();
        CollisionGrid grid = WorldObjects.collision(state, LANE, lane());
        WorldPosition here = state.position();
        BitSet reachable = grid.reachableFrom(lane().tileOf(here.x()), lane().tileOf(here.y()));
        for (int y = 0; y < lane().height(); y++) {
            for (int x = 0; x < lane().width(); x++) {
                if (o.distanceTo(x, y) == distance && reachable.get(y * lane().width() + x)) {
                    return new int[] {lane().centre(x), lane().centre(y)};
                }
            }
        }
        throw new AssertionError("No spot " + distance + " tiles from " + creepName);
    }

    private SessionView encounter(String creepName, int distance) {
        clock.advance(Duration.ofMinutes(1));
        int[] spot = spotAt(creepName, distance);
        return engine.encounter(state, creepName, spot[0], spot[1], Facing.DOWN);
    }

    private SessionView winBattle() {
        SessionView v = engine.view(state);
        while (v.mode() == Mode.BATTLE) {
            v = engine.battleAction(state, BattleAction.of(BattleAction.Kind.STRIKE));
        }
        return v;
    }

    @Test
    void theWorldViewListsLivingCreepsWithTheirLevelsAndAggro() {
        WorldView world = engine.view(state).world();
        WorldView.CreepView slime = world.creeps().stream().filter(c -> c.id().equals(SLIME)).findFirst().orElseThrow();
        assertEquals("mire_slime", slime.template());
        assertEquals("creep.mire_slime", slime.sprite());
        assertEquals(1, slime.aggroRadius());
        assertTrue(slime.level() >= 1 && slime.level() <= 2, "rolled within levelMin..levelMax");
        assertFalse(slime.wary());
    }

    @Test
    void walkingIntoTheAggroRadiusStartsABattle() {
        SessionView v = encounter(SLIME, 1);
        assertEquals(Mode.BATTLE, v.mode());
        assertEquals("Mire Slime", v.battle().enemy().name());
        assertEquals(state.creeps().level(CreepRoster.key(LANE, SLIME)), v.battle().enemy().level());
        assertEquals(4, v.battle().menu().skills().size());
        assertEquals("Rending Cut", v.battle().menu().skills().getFirst().name());
        assertNull(v.battle().menu().skills().get(2).id(), "two of the four slots start empty (P-55)");
    }

    @Test
    void anEncounterOutsideTheRadiusOrNamingAnotherCreepIsRejected() {
        int radius = creep(SLIME).aggroRadius();
        WorldPosition before = state.position();
        assertThrows(WorldEventRejectedException.class, () -> encounter(SLIME, radius + 1));
        assertThrows(WorldEventRejectedException.class, () -> {
            clock.advance(Duration.ofMinutes(1));
            int[] spot = spotAt(SLIME, 1);
            engine.encounter(state, RAT, spot[0], spot[1], Facing.DOWN);
        });
        assertEquals(before, state.position());
        assertEquals(Mode.WORLD, state.mode());
    }

    @Test
    void aDefeatedCreepStaysDownAndPaysShards() {
        encounter(SLIME, 1);
        SessionView after = winBattle();
        assertEquals(Mode.WORLD, after.mode(), "a creep's battle returns to the world");
        assertEquals("WON", after.battle().outcome(), "the last round is shown once");
        assertTrue(after.hud().shards() > 0);
        assertNull(engine.view(state).battle(), "...and only once");
        assertTrue(engine.view(state).world().creeps().stream().noneMatch(c -> c.id().equals(SLIME)));
        assertThrows(WorldEventRejectedException.class, () -> encounter(SLIME, 1));
    }

    @Test
    void aCreepFledFromIgnoresThePlayerForAWhile() {
        encounter(RAT, 1);
        SessionView v = engine.view(state);
        for (int tries = 0; tries < 30 && v.mode() == Mode.BATTLE; tries++) {
            state.stats().heal(100);
            v = engine.battleAction(state, BattleAction.of(BattleAction.Kind.FLEE));
        }
        assertEquals("FLED", v.battle().outcome());
        assertTrue(engine.view(state).world().creeps().stream().anyMatch(c -> c.id().equals(RAT) && c.wary()));
        int[] spot = spotAt(RAT, 1);
        assertThrows(WorldEventRejectedException.class,
                () -> engine.encounter(state, RAT, spot[0], spot[1], Facing.DOWN), "too soon to have walked away and back");
        clock.advance(Duration.ofMinutes(1));
        assertEquals(Mode.BATTLE, engine.encounter(state, RAT, spot[0], spot[1], Facing.DOWN).mode());
    }

    @Test
    void defeatByACreepWakesThePlayerAtHomeWithShardsKeptAndCreepsBack() {
        encounter(SLIME, 1);
        winBattle();
        state.stats().addShards(100);
        int shards = state.stats().shards();
        encounter(RAT, 1);
        SessionView v = engine.view(state);
        while (v.mode() == Mode.BATTLE) {
            state.stats().damage(1000);                                // the next round ends it
            v = engine.battleAction(state, BattleAction.of(BattleAction.Kind.GUARD));
        }
        assertEquals("LOST", v.battle().outcome());
        assertEquals(Mode.WORLD, v.mode());
        AreaMap lane = lane();
        assertEquals(WorldPosition.at(LANE, lane, lane.spawn("home").orElseThrow()).x(), state.position().x());
        assertEquals(state.stats().maxHp(), state.stats().hp());
        assertEquals(state.stats().maxFp(), state.stats().fp());
        assertEquals(shards, state.stats().shards(), "held Shards are kept until Lost Shards exist (P-55)");
        assertTrue(v.world().creeps().stream().anyMatch(c -> c.id().equals(SLIME)), "the slime is back");
        assertTrue(v.toasts().stream().anyMatch(t -> t.text().contains("You wake at home")));
    }

    @Test
    void battleActionsTheBattleDoesNotAllowAreRefused() {
        encounter(SLIME, 1);
        assertThrows(ActionNotAllowedException.class, () -> engine.battleAction(state, BattleAction.skill(3)),
                "an empty slot");
        state.stats().spendFp(state.stats().fp());
        assertThrows(ActionNotAllowedException.class, () -> engine.battleAction(state, BattleAction.skill(0)),
                "not enough FP");
        assertThrows(ActionNotAllowedException.class, () -> engine.battleAction(state, BattleAction.item("heartshard")));
        assertNotNull(engine.view(state).battle());
    }
}
