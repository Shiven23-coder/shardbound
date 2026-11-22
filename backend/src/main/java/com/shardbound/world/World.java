package com.shardbound.world;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Regions, areas, the area graph and the loaded maps (content/regions.json
 * and content/maps). Immutable and shared by every session.
 */
public record World(Start start, Map<String, Region> regions, Map<String, Area> areas, List<Edge> edges,
                    Map<String, AreaMap> maps) {

    public World {
        regions = Map.copyOf(regions);
        areas = Map.copyOf(areas);
        edges = List.copyOf(edges);
        maps = Map.copyOf(maps);
    }

    /** Where a new game begins. */
    public record Start(String area, String spawn, String dialogue, Map<String, Integer> flags) {
        public Start {
            flags = Map.copyOf(flags);
        }
    }

    /**
     * A region and its light states in priority order: the first whose flag
     * is set wins; the last has no flag and is the default.
     */
    public record Region(String id, String name, String music, List<LightState> lightStates) {
        public Region {
            lightStates = List.copyOf(lightStates);
        }
    }

    /** A named lighting state (ambient, rain…; see content/art/grades.json) and the flag that turns it on. */
    public record LightState(String id, @Nullable String flag) { }

    /** An area; {@code map} is null for an area that is not built yet. */
    public record Area(String id, String region, String name, @Nullable String map) { }

    /** An undirected connection between two areas, closed until {@code gateFlag} is set. */
    public record Edge(String from, String to, int weight, @Nullable String gateFlag) {
        public boolean joins(String a, String b) {
            return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
        }
    }

    public Area area(String id) {
        Area area = areas.get(id);
        if (area == null) {
            throw new IllegalArgumentException("Unknown area " + id);
        }
        return area;
    }

    /** The map of an area that has one. */
    public AreaMap mapOf(String areaId) {
        AreaMap map = maps.get(areaId);
        if (map == null) {
            throw new IllegalArgumentException("Area " + areaId + " has no map");
        }
        return map;
    }

    public Optional<Edge> edgeBetween(String a, String b) {
        return edges.stream().filter(e -> e.joins(a, b)).findFirst();
    }
}
