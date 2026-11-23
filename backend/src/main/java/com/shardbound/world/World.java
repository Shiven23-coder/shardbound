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

    /** Where a new game begins, and what the new character carries. */
    public record Start(String area, String spawn, String dialogue, Map<String, Integer> flags, Kit kit) {
        public Start {
            flags = Map.copyOf(flags);
        }
    }

    /**
     * The starting kit (P-55): the equipped weapon, the skills in the loadout,
     * the bag (item id → quantity, in file order) and gold.
     */
    public record Kit(String weapon, List<String> skills, Map<String, Integer> bag, int gold) {
        public Kit {
            skills = List.copyOf(skills);
            bag = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(bag));
        }
    }

    /** A place to wake: an area and one of its spawn points. */
    public record Place(String area, String spawn) { }

    /**
     * A region and its light states in priority order: the first whose flag
     * is set wins; the last has no flag and is the default. {@code respawn} is
     * where a player defeated by a creep wakes until Lanterns exist (P-55).
     */
    public record Region(String id, String name, String music, Place respawn, List<LightState> lightStates) {
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

    /** The Lanterns of an area (P-61). */
    public List<Lantern> lanternsIn(String areaId) {
        AreaMap map = maps.get(areaId);
        return map == null ? List.of()
                : map.objectsOf(ObjectType.LANTERN).stream().map(o -> Lantern.of(areaId, o)).toList();
    }

    /** A Lantern by its id, anywhere in the world: a scan of a few objects per map. */
    public Optional<Lantern> lantern(String lanternId) {
        return maps.keySet().stream().flatMap(area -> lanternsIn(area).stream())
                .filter(l -> l.id().equals(lanternId)).findFirst();
    }
}
