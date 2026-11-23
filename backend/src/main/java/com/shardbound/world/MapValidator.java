package com.shardbound.world;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the world at startup (§9): the start, regions, areas and the area
 * graph, and in every map the structure, every referenced id, that every
 * spawn point, exit, NPC, interact, trigger and obstacle can be reached by BFS
 * from the map's main spawn (with every obstacle open), and that every building
 * footprint is blocked in the collision layer (P-43), so the client, which reads
 * only that layer, walks where the server does. Errors stop startup.
 */
public class MapValidator {

    private final World world;
    private final Set<String> dialogues;
    private final Set<String> npcs;
    private final Set<String> creeps;
    private final Set<String> lanternIds = new HashSet<>();
    private final List<String> errors = new ArrayList<>();

    /** The aggro radius a creep spawn may have, in tiles (V2_SPEC §4.2). */
    public static final int MIN_AGGRO = 1;
    public static final int MAX_AGGRO = 3;

    /**
     * @param dialogues every dialogue id in the content
     * @param npcs      every NPC id in world.json
     * @param creeps    every enemy id in creeps.json
     */
    public MapValidator(World world, Set<String> dialogues, Set<String> npcs, Set<String> creeps) {
        this.world = world;
        this.dialogues = dialogues;
        this.npcs = npcs;
        this.creeps = creeps;
    }

    /** Runs every check; returns the errors (empty when valid). Time O(areas · tiles). */
    public List<String> validate() {
        checkStart();
        checkAreasAndEdges();
        for (Map.Entry<String, AreaMap> entry : world.maps().entrySet()) {
            checkMap(entry.getKey(), entry.getValue());
        }
        return errors;
    }

    private void checkStart() {
        World.Start start = world.start();
        AreaMap map = world.maps().get(start.area());
        if (map == null) {
            errors.add("Start area " + start.area() + " has no map");
        } else if (map.spawn(start.spawn()).isEmpty()) {
            errors.add("Start spawn " + start.spawn() + " is not in " + start.area());
        }
        if (!dialogues.contains(start.dialogue())) {
            errors.add("Start dialogue " + start.dialogue() + " does not exist");
        }
    }

    private void checkAreasAndEdges() {
        for (World.Region region : world.regions().values()) {
            AreaMap respawnMap = world.maps().get(region.respawn().area());
            if (respawnMap == null || respawnMap.spawn(region.respawn().spawn()).isEmpty()) {
                errors.add("Region " + region.id() + ": respawn " + region.respawn().area() + "/"
                        + region.respawn().spawn() + " is not a spawn point of a built area");
            }
            List<World.LightState> states = region.lightStates();
            if (states.getLast().flag() != null) {
                errors.add("Region " + region.id() + ": the last light state must have no flag (it is the default)");
            }
        }
        for (World.Area area : world.areas().values()) {
            if (!world.regions().containsKey(area.region())) {
                errors.add("Area " + area.id() + " is in unknown region " + area.region());
            }
        }
        for (World.Edge edge : world.edges()) {
            for (String end : List.of(edge.from(), edge.to())) {
                if (!world.areas().containsKey(end)) {
                    errors.add("Edge " + edge.from() + " – " + edge.to() + " names unknown area " + end);
                }
            }
            for (String end : List.of(edge.from(), edge.to())) {
                if (world.areas().containsKey(end) && !world.maps().containsKey(end)) {
                    errors.add("Edge " + edge.from() + " – " + edge.to() + ": area " + end
                            + " has no map; edges only join built areas (P-45)");
                }
            }
        }
    }

    private void checkMap(String areaId, AreaMap map) {
        String where = "map " + map.key();
        List<MapObject> mains = map.objectsOf(ObjectType.SPAWN_POINT).stream().filter(o -> o.flag("main")).toList();
        if (mains.size() != 1) {
            errors.add(where + ": needs exactly one main spawn_point, has " + mains.size());
            return;
        }
        for (MapObject o : map.objects()) {
            checkReferences(areaId, where, o);
        }
        checkBuildings(where, map);
        MapObject main = mains.getFirst();
        BitSet reachable = map.collisionWith(o -> false).reachableFrom(main.x(), main.y());
        for (MapObject o : map.objects()) {
            boolean mustReach = o.type() == ObjectType.SPAWN_POINT || o.type() == ObjectType.EXIT
                    || o.type() == ObjectType.CREEP_SPAWN || o.type().interactive();
            if (mustReach && !reachesAny(map, reachable, o)) {
                errors.add(where + ": " + o.type() + " " + o.name() + " cannot be reached from " + main.name());
            }
        }
    }

    private void checkReferences(String areaId, String where, MapObject o) {
        switch (o.type()) {
            case EXIT -> checkExit(areaId, where, o);
            case NPC -> {
                if (!npcs.contains(String.valueOf(o.property("npcId")))) {
                    errors.add(where + ": NPC " + o.name() + " names unknown npcId " + o.property("npcId"));
                }
                checkDialogue(where, o);
            }
            case INTERACT, TRIGGER, OBSTACLE -> checkDialogue(where, o);
            case CREEP_SPAWN -> checkCreep(where, o);
            case LANTERN -> checkLantern(areaId, where, o);
            default -> { }
        }
    }

    /**
     * A Lantern (P-61) has an id unique in the world, a name, a spawn point of its own map for the
     * player to wake at, and a dialogue for its messages.
     */
    private void checkLantern(String areaId, String where, MapObject o) {
        String id = o.property("lanternId");
        if (id == null || id.isBlank() || o.property("name") == null) {
            errors.add(where + ": Lantern " + o.name() + " needs a lanternId and a name");
            return;
        }
        if (!lanternIds.add(id)) {
            errors.add(where + ": Lantern id " + id + " is used twice");
        }
        AreaMap map = world.maps().get(areaId);
        if (o.property("spawn") == null || map.spawn(o.property("spawn")).isEmpty()) {
            errors.add(where + ": Lantern " + o.name() + " names no spawn point of its map");
        }
        checkDialogue(where, o);
    }

    /** A creep spawn names a known enemy, a level range 1 ≤ min ≤ max ≤ 99 and an aggro radius of 1–3 tiles. */
    private void checkCreep(String where, MapObject o) {
        if (!creeps.contains(String.valueOf(o.property("template")))) {
            errors.add(where + ": creep " + o.name() + " names unknown template " + o.property("template"));
        }
        Integer min = integer(o, "levelMin");
        Integer max = integer(o, "levelMax");
        if (min == null || max == null || min < 1 || max < min || max > 99) {
            errors.add(where + ": creep " + o.name() + " needs levelMin and levelMax with 1 ≤ min ≤ max ≤ 99");
        }
        Integer aggro = integer(o, "aggroRadius");
        if (aggro == null || aggro < MIN_AGGRO || aggro > MAX_AGGRO) {
            errors.add(where + ": creep " + o.name() + " needs an aggroRadius of " + MIN_AGGRO + "–" + MAX_AGGRO);
        }
        if (o.width() != 1 || o.height() != 1) {
            errors.add(where + ": creep " + o.name() + " must be one tile");
        }
    }

    private static Integer integer(MapObject o, String key) {
        try {
            return Integer.valueOf(o.property(key));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Every tile of a building's footprint blocks in the collision layer itself. */
    private void checkBuildings(String where, AreaMap map) {
        for (MapObject b : map.objectsOf(ObjectType.BUILDING)) {
            for (int y = b.y(); y < b.y() + b.height(); y++) {
                for (int x = b.x(); x < b.x() + b.width(); x++) {
                    if (map.collision().walkable(x, y)) {
                        errors.add(where + ": building " + b.name() + " is open at " + x + "," + y
                                + " in the collision layer; a building blocks its whole footprint (P-43)");
                    }
                }
            }
        }
    }

    private void checkExit(String areaId, String where, MapObject exit) {
        String toArea = exit.property("toArea");
        String toSpawn = exit.property("toSpawn");
        if (toArea == null || !world.areas().containsKey(toArea)) {
            errors.add(where + ": exit " + exit.name() + " leads to unknown area " + toArea);
            return;
        }
        if (world.edgeBetween(areaId, toArea).isEmpty()) {
            errors.add(where + ": exit " + exit.name() + " has no edge " + areaId + " – " + toArea + " in regions.json");
        }
        AreaMap target = world.maps().get(toArea);
        if (target != null && (toSpawn == null || target.spawn(toSpawn).isEmpty())) {
            errors.add(where + ": exit " + exit.name() + " leads to spawn " + toSpawn + ", which " + toArea + " does not have");
        }
    }

    private void checkDialogue(String where, MapObject o) {
        String dialogue = o.property("dialogue");
        if (dialogue == null || !dialogues.contains(dialogue)) {
            errors.add(where + ": " + o.name() + " names unknown dialogue " + dialogue);
        }
    }

    /** True if a tile of the object, or a tile next to one (a sign, an NPC), is reachable. */
    private static boolean reachesAny(AreaMap map, BitSet reachable, MapObject o) {
        int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = o.y(); y < o.y() + o.height(); y++) {
            for (int x = o.x(); x < o.x() + o.width(); x++) {
                for (int[] d : around) {
                    int nx = x + d[0];
                    int ny = y + d[1];
                    if (map.collision().inBounds(nx, ny) && reachable.get(ny * map.width() + nx)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
