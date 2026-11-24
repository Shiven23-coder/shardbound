package com.shardbound.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Map rules on small hand-made maps: solid buildings (P-43), obstacles that
 * block only while active (P-45) and exits only to built areas.
 */
class MapValidatorTest {

    private static final int SIZE = 8;

    /** An 8 × 8 map with a blocked border, the main spawn at (1, 1) and the given extra blocked tiles and objects. */
    private static AreaMap map(List<int[]> blockedTiles, MapObject... extra) {
        BitSet blocked = new BitSet(SIZE * SIZE);
        for (int i = 0; i < SIZE; i++) {
            blocked.set(i);
            blocked.set((SIZE - 1) * SIZE + i);
            blocked.set(i * SIZE);
            blocked.set(i * SIZE + SIZE - 1);
        }
        blockedTiles.forEach(t -> blocked.set(t[1] * SIZE + t[0]));
        List<MapObject> objects = new ArrayList<>();
        objects.add(new MapObject("home", ObjectType.SPAWN_POINT, 1, 1, 1, 1, Map.of("main", "true")));
        objects.addAll(List.of(extra));
        return new AreaMap("test_map", SIZE, SIZE, 16, new CollisionGrid(SIZE, SIZE, blocked), objects);
    }

    private static List<String> validate(AreaMap map, World.Edge... edges) {
        World world = new World(
                new World.Start("test_area", "home", "intro", Map.of(), new World.Kit("blade", List.of(), Map.of(), 0)),
                Map.of("test_region", new World.Region("test_region", "Test", "test", new World.Place("test_area", "home"),
                        List.of(new World.LightState("day", null)))),
                Map.of("test_area", new World.Area("test_area", "test_region", "Test", "test_map"),
                        "unbuilt_area", new World.Area("unbuilt_area", "test_region", "Unbuilt", null)),
                List.of(edges),
                Map.of("test_area", map));
        return new MapValidator(world, Set.of("intro", "flavour"), Set.of(), Set.of("storm_rat"),
                Map.of("hatchet", false, "tonic", true)).validate();
    }

    private static MapObject building(int x, int y, int w, int h) {
        return new MapObject("house", ObjectType.BUILDING, x, y, w, h, Map.of());
    }

    private static MapObject obstacle(String hideIf) {
        return new MapObject("crates", ObjectType.OBSTACLE, 3, 1, 1, 6,
                hideIf == null ? Map.of("dialogue", "flavour") : Map.of("dialogue", "flavour", "hideIf", hideIf));
    }

    @Test
    void aBuildingWithAnOpenTileInItsFootprintIsRejected() {
        AreaMap map = map(List.of(new int[] {3, 3}, new int[] {4, 3}), building(3, 2, 2, 2));   // roof row (y = 2) left open
        assertTrue(validate(map).stream().anyMatch(e -> e.contains("building house") && e.contains("3,2")),
                validate(map).toString());
    }

    @Test
    void aBuildingWhoseFootprintIsBlockedPasses() {
        AreaMap map = map(List.of(new int[] {3, 2}, new int[] {4, 2}, new int[] {3, 3}, new int[] {4, 3}), building(3, 2, 2, 2));
        assertEquals(List.of(), validate(map));
    }

    @Test
    void theServerGridBlocksEveryBuildingFootprint() {
        AreaMap map = map(List.of(), building(3, 2, 2, 2));
        CollisionGrid grid = map.collisionWith(o -> true);
        assertFalse(grid.walkable(3, 2));
        assertFalse(grid.walkable(4, 3));
        assertTrue(grid.walkable(5, 2));
    }

    @Test
    void anObstacleBlocksOnlyWhileItIsActive() {
        AreaMap map = map(List.of(), obstacle("following"));
        assertFalse(map.collisionWith(o -> true).walkable(3, 4), "active: blocked");
        assertTrue(map.collisionWith(o -> false).walkable(3, 4), "removed: walkable");
        assertTrue(map.collision().walkable(3, 4), "the collision layer itself stays open");
    }

    @Test
    void objectsBehindAnObstacleCountAsReachableAndTheObstacleItselfMustBeReachable() {
        MapObject behind = new MapObject("sign", ObjectType.INTERACT, 5, 4, 1, 1, Map.of("dialogue", "flavour"));
        assertEquals(List.of(), validate(map(List.of(), obstacle("following"), behind)));

        MapObject walledIn = new MapObject("rubble", ObjectType.OBSTACLE, 5, 5, 1, 1, Map.of("dialogue", "flavour"));
        List<int[]> wall = List.of(new int[] {4, 4}, new int[] {5, 4}, new int[] {6, 4}, new int[] {4, 5}, new int[] {4, 6});
        assertTrue(validate(map(wall, walledIn)).stream().anyMatch(e -> e.contains("OBSTACLE rubble")));
    }

    @Test
    void anObstacleNeedsFlavourText() {
        MapObject mute = new MapObject("crates", ObjectType.OBSTACLE, 3, 1, 1, 6, Map.of());
        assertTrue(validate(map(List.of(), mute)).stream().anyMatch(e -> e.contains("crates") && e.contains("dialogue")));
    }

    @Test
    void anEdgeMayOnlyJoinBuiltAreas() {
        List<String> errors = validate(map(List.of()), new World.Edge("test_area", "unbuilt_area", 1, "some_flag"));
        assertTrue(errors.stream().anyMatch(e -> e.contains("unbuilt_area") && e.contains("no map")), errors.toString());
    }
    private static MapObject chest(String name, Map<String, String> props) {
        return new MapObject(name, ObjectType.CHEST, 4, 4, 1, 1, props);
    }

    @Test
    void aChestWithAKnownItemAFlagAndAFallbackIsValid() {
        assertEquals(List.of(), validate(map(List.of(),
                chest("box", Map.of("item", "hatchet", "qty", "1", "flag", "chest_box", "fallbackLevel", "3")),
                chest("crate", Map.of("item", "tonic", "qty", "2", "flag", "chest_crate")))));
    }

    @Test
    void aChestNeedsAKnownItemAndAFlagOfItsOwn() {
        List<String> errors = validate(map(List.of(),
                chest("box", Map.of("item", "sword", "qty", "1", "flag", "chest_box")),
                chest("crate", Map.of("item", "tonic", "qty", "1", "flag", "chest_box")),
                chest("bare", Map.of("item", "tonic", "qty", "1"))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("box") && e.contains("unknown item sword")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("chest_box") && e.contains("twice")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("bare") && e.contains("flag")), errors.toString());
    }

    @Test
    void aChestWithAnItemThatDoesNotStackNeedsAFallbackAndQuantityOne() {
        List<String> errors = validate(map(List.of(),
                chest("box", Map.of("item", "hatchet", "qty", "1", "flag", "chest_box")),
                chest("pair", Map.of("item", "hatchet", "qty", "2", "flag", "chest_pair", "fallbackItem", "tonic")),
                chest("odd", Map.of("item", "hatchet", "qty", "1", "flag", "chest_odd", "fallbackItem", "rope"))));
        assertTrue(errors.stream().anyMatch(e -> e.contains("box") && e.contains("fallback")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("pair") && e.contains("qty 1")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("odd") && e.contains("unknown fallback item rope")),
                errors.toString());
    }
}
