package com.shardbound.engine;

import com.shardbound.exception.WorldEventRejectedException;
import com.shardbound.support.MutableClock;
import com.shardbound.view.SessionView;
import com.shardbound.world.AreaMap;
import com.shardbound.world.CollisionGrid;
import com.shardbound.world.Facing;
import com.shardbound.world.MapObject;
import com.shardbound.world.ObjectType;
import com.shardbound.world.WorldPosition;
import com.shardbound.world.WorldRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server-side checks on world events (§4.11), on the real Eldmere maps. */
class WorldEventTest {

    private final MutableClock clock = new MutableClock();
    private GameEngine engine;
    private RoutePlayer player;
    private GameState state;

    @BeforeEach
    void inTheWorld() {
        engine = TestEngines.inMemory(clock);
        state = engine.newGame(0, false, 1L);
        player = new RoutePlayer(engine, state, clock);
        player.choose("Ignore");                                    // the opening ends: WORLD mode
    }

    private AreaMap lane() {
        return engine.content().world().mapOf("eldmere_cottage_lane");
    }

    private int[] centreOf(MapObject o) {
        return new int[] {lane().centre(o.x()), lane().centre(o.y())};
    }

    @Test
    void anExitMovesThePlayerToTheNextAreasSpawn() {
        SessionView v = player.exit("to_ruins");
        AreaMap ruins = engine.content().world().mapOf("eldmere_old_ruins");
        MapObject spawn = ruins.spawn("from_lane").orElseThrow();
        assertEquals("eldmere_old_ruins", v.world().areaId());
        assertEquals(ruins.centre(spawn.x()), v.world().player().x());
        assertEquals(ruins.centre(spawn.y()), v.world().player().y());
    }

    @Test
    void eventsAreRejectedOutsideWorldMode() {
        GameState fresh = engine.newGame(0, false, 2L);               // still in the opening dialogue
        WorldPosition start = fresh.position();
        WorldEventRejectedException e = assertThrows(WorldEventRejectedException.class,
                () -> engine.exit(fresh, "to_ruins", start.x(), start.y(), Facing.UP));
        assertEquals(start, e.lastPosition());
    }

    @Test
    void aTeleportFasterThanWalkingIsRejectedAndReportsTheLastPosition() {
        WorldPosition before = state.position();
        int[] exit = centreOf(lane().object("to_ruins").orElseThrow());
        WorldEventRejectedException e = assertThrows(WorldEventRejectedException.class,
                () -> engine.exit(state, "to_ruins", exit[0], exit[1], Facing.UP));   // no time has passed
        assertEquals(before, e.lastPosition());
        assertEquals(before, state.position(), "a rejected event changes nothing");
    }

    @Test
    void theSameWalkIsAcceptedOnceEnoughTimeHasPassed() {
        int[] exit = centreOf(lane().object("to_ruins").orElseThrow());
        clock.advance(Duration.ofSeconds(10));
        SessionView v = engine.exit(state, "to_ruins", exit[0], exit[1], Facing.UP);
        assertEquals("eldmere_old_ruins", v.world().areaId());
    }

    @Test
    void aPositionOffATileCentreIsRejected() {                                   // P-51: the player is always on a whole tile
        int[] exit = centreOf(lane().object("to_ruins").orElseThrow());
        clock.advance(Duration.ofMinutes(1));
        WorldPosition before = state.position();
        WorldEventRejectedException e = assertThrows(WorldEventRejectedException.class,
                () -> engine.exit(state, "to_ruins", exit[0] + 3, exit[1], Facing.UP));
        assertEquals(before, e.lastPosition());
    }

    @Test
    void theSpeedCheckCountsTilesPerSecondFromTheStepTime() {                    // P-51
        WorldRules rules = engine.content().worldRules();
        assertEquals(1000.0 / rules.stepMs(), rules.tilesPerSecond(), 1e-9);
        MapObject exit = lane().object("to_ruins").orElseThrow();
        WorldPosition home = state.position();
        int tiles = WorldObjects.collision(state, "eldmere_cottage_lane", lane())
                .pathLength(lane().tileOf(home.x()), lane().tileOf(home.y()), exit.x(), exit.y()).orElseThrow();
        assertTrue(tiles > rules.speedSlackTiles() + 4, "a walk long enough to measure: " + tiles);
        // Too fast: the time for the tiles beyond the slack, at the tolerated speed, less a step.
        long tooSoon = (long) ((tiles - rules.speedSlackTiles()) / (rules.tilesPerSecond() * rules.speedTolerance()) * 1000)
                - rules.stepMs();
        clock.advance(Duration.ofMillis(tooSoon));
        assertThrows(WorldEventRejectedException.class,
                () -> engine.exit(state, "to_ruins", lane().centre(exit.x()), lane().centre(exit.y()), Facing.UP));
        // Walking it one step per stepMs is always accepted.
        clock.advance(Duration.ofMillis((long) tiles * rules.stepMs() - tooSoon));
        SessionView v = engine.exit(state, "to_ruins", lane().centre(exit.x()), lane().centre(exit.y()), Facing.UP);
        assertEquals("eldmere_old_ruins", v.world().areaId());
    }

    @Test
    void standingInAWallIsRejected() {
        clock.advance(Duration.ofMinutes(1));
        assertThrows(WorldEventRejectedException.class,
                () -> engine.exit(state, "to_ruins", 8, 8, Facing.UP));   // tile (0, 0) is forest
    }

    @Test
    void anExitMustBeStoodOn() {
        clock.advance(Duration.ofMinutes(1));
        WorldPosition here = state.position();
        assertThrows(WorldEventRejectedException.class,
                () -> engine.exit(state, "to_ruins", here.x(), here.y(), Facing.UP));
    }

    @Test
    void anAffinityChangeShowsAStoryCueAndKeepsTheNumberInTheCharacterSheet() {
        RoutePlayer helper = new RoutePlayer(engine, engine.newGame(0, false, 5L), clock);
        SessionView v = helper.choose("Help Corvin");
        assertTrue(v.toasts().stream().anyMatch(t -> t.kind().equals("AFFINITY") && t.text().equals("Corvin will remember that.")),
                v.toasts().toString());
        assertTrue(v.toasts().stream().noneMatch(t -> t.text().matches(".*[+-]\\d.*")), "no numbers in the toast");
        assertTrue(v.affinity().stream().anyMatch(a -> a.value() == 10), "the number stays in the Bag: " + v.affinity());
    }

    @Test
    void aNodeWhoseOnlyOptionJustClosesIsAMessage() {
        GameState fresh = engine.newGame(0, false, 3L);
        assertFalse(engine.view(fresh).dialogue().message(), "the storm intro asks a real question");
        SessionView flood = player.interact("flooded_road");
        assertTrue(flood.dialogue().message(), "the flooded road only says why the way is closed");
        player.choose("Turn back");
        player.exit("to_ruins");
        assertFalse(player.interact("crater_crystal").dialogue().message(), "taking the crystal is a choice");
        assertTrue(player.choose("Take the crystal").dialogue().message(), "'Walk on' only closes the page");
    }

    @Test
    void theCurrentObjectiveFollowsTheStory() {                                   // P-46
        assertEquals("Follow the green light to the Old Ruins.", engine.view(state).objective());
        player.exit("to_ruins");
        player.interact("crater_crystal");
        player.choose("Take the crystal");
        SessionView dawn = player.choose("Walk on");
        assertEquals("Take the crystal to someone in Eldmere Square.", dawn.objective());
        state.setFlag("following_corvin", 1);
        assertEquals("Follow Corvin into Tanner's Alley.", engine.view(state).objective());
    }

    @Test
    void everyBuildingInEldmereBlocksItsWholeFootprint() {                       // P-43
        int buildings = 0;
        for (AreaMap map : engine.content().world().maps().values()) {
            CollisionGrid grid = map.collisionWith(o -> true);
            for (MapObject b : map.objectsOf(ObjectType.BUILDING)) {
                buildings++;
                for (int y = b.y(); y < b.y() + b.height(); y++) {
                    for (int x = b.x(); x < b.x() + b.width(); x++) {
                        assertFalse(map.collision().walkable(x, y), map.key() + " " + b.name() + " at " + x + "," + y);
                        assertFalse(grid.walkable(x, y));
                    }
                }
            }
        }
        assertTrue(buildings >= 8, "the cottage, the mill, the watchtower and the Square's houses: " + buildings);
    }

    @Test
    void theCottageDoorIsBarredAgainstTheStorm() {                                // P-43
        MapObject door = lane().object("cottage_door").orElseThrow();
        assertEquals(ObjectType.INTERACT, door.type());
        SessionView v = player.interact("cottage_door");
        assertTrue(v.dialogue().text().contains("barred against the storm"), v.dialogue().text());
    }

    @Test
    void theFloodedRoadClosesTheWayWestInsteadOfAnExit() {                         // P-45
        assertTrue(lane().objectsOf(ObjectType.EXIT).stream().noneMatch(e -> "whispering_wood_edge".equals(e.property("toArea"))));
        MapObject flood = lane().object("flooded_road").orElseThrow();
        assertEquals(ObjectType.OBSTACLE, flood.type());
        assertFalse(WorldObjects.collision(state, "eldmere_cottage_lane", lane()).walkable(flood.x(), flood.y()));
        SessionView v = player.interact("flooded_road");
        assertEquals("DIALOGUE", v.mode().name());
    }

    @Test
    void cratesBlockTannersAlleyUntilThePlayerFollowsCorvin() {                     // P-45
        AreaMap square = engine.content().world().mapOf("eldmere_square");
        MapObject crates = square.object("alley_crates").orElseThrow();
        assertFalse(WorldObjects.collision(state, "eldmere_square", square).walkable(crates.x(), crates.y()));
        state.setFlag("following_corvin", 1);
        assertTrue(WorldObjects.collision(state, "eldmere_square", square).walkable(crates.x(), crates.y()));
        assertFalse(WorldObjects.active(state, "eldmere_square", crates), "gone, so the client stops drawing them");
    }

    @Test
    void anInteractionOutOfRangeIsRejected() {
        clock.advance(Duration.ofMinutes(1));
        WorldPosition here = state.position();                          // home, far from Corvin
        assertThrows(WorldEventRejectedException.class,
                () -> engine.interact(state, "corvin", here.x(), here.y(), Facing.DOWN));
    }

    @Test
    void anOnceOnlyTriggerFiresOnce() {
        player.exit("to_ruins");
        player.interact("crater_crystal");
        player.choose("Take the crystal");
        player.choose("Walk on");
        player.exit("to_lane");
        player.exit("to_square");
        player.interact("square_dawn");
        SessionView v = player.choose("Look around");
        assertTrue(v.world().hiddenObjects().contains("square_dawn"));
        assertThrows(WorldEventRejectedException.class, () -> player.interact("square_dawn"));
    }

    @Test
    void npcsFollowTheirFlags() {
        assertTrue(engine.view(state).world().npcs().stream().anyMatch(n -> n.id().equals("corvin")));
        player.exit("to_ruins");
        player.interact("crater_crystal");
        player.choose("Take the crystal");
        player.choose("Walk on");
        SessionView v = player.exit("to_lane");
        assertTrue(v.world().hiddenObjects().contains("corvin"), "Corvin has left the lane by dawn");
    }
}
