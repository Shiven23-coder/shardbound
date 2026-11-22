package com.shardbound.save;

import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Set;

/**
 * Format 1 → 2 (V3): the v1 prologue was a sequence of story pages; in
 * format 2 the player stands somewhere in the world and the same pages are
 * dialogue nodes. The node is kept (every v1 node still exists), and the
 * player is placed at the spawn point of the area that page took place in,
 * with the flags the world now reads for the time of night.
 *
 * <p>The table is frozen v1 knowledge: it never changes with later content.
 */
final class WorldPositionUpgrade implements SaveUpgrade {

    private record Place(String area, String spawn) { }

    private static final Place LANE = new Place("eldmere_cottage_lane", "home");
    private static final Place RUINS = new Place("eldmere_old_ruins", "from_lane");
    private static final Place SQUARE = new Place("eldmere_square", "from_lane");
    private static final Place MILL = new Place("eldmere_mill_road", "from_square");

    /** v1 node → where it took place; unlisted nodes (the alley, the raid, endings) are in the Square. */
    private static final Map<String, Place> PLACES = Map.of(
            "p01", LANE, "p02", RUINS, "p04w", LANE, "p04h", MILL);
    /** v1 nodes that happen during the storm night, before the Heartshard is taken. */
    private static final Set<String> STORM_NIGHT = Set.of("p01", "p02");
    /** v1 nodes reached by following Corvin. */
    private static final Set<String> FOLLOWING_CORVIN = Set.of("p06m", "p07m", "p07l", "p08m", "p09m", "p10m");

    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public ObjectNode apply(ObjectNode document) {
        String node = document.path("currentNode").asString();
        Place place = PLACES.getOrDefault(node, SQUARE);
        ObjectNode world = document.putObject("world");
        world.put("area", place.area());
        world.put("spawn", place.spawn());
        world.put("x", 0);
        world.put("y", 0);
        world.put("facing", "DOWN");
        ObjectNode flags = document.withObject("flags");
        if (STORM_NIGHT.contains(node)) {
            flags.put("storm_night", 1);
        } else {
            flags.put("dawn", 1);
        }
        if (FOLLOWING_CORVIN.contains(node)) {
            flags.put("following_corvin", 1);
        }
        return document;
    }
}
