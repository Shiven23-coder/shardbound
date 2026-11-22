package com.shardbound.content;

import com.shardbound.exception.ContentValidationException;
import com.shardbound.world.AreaMap;
import com.shardbound.world.CollisionGrid;
import com.shardbound.world.MapObject;
import com.shardbound.world.ObjectType;
import com.shardbound.world.World;
import com.shardbound.world.WorldRules;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads regions.json, balance.json and the Tiled maps ({@code maps/*.tmj})
 * into the {@link World} and {@link WorldRules}. Structure and references are
 * checked afterwards by {@link com.shardbound.world.MapValidator}.
 */
public class WorldReader {

    private static final String COLLISION_LAYER = "collision";
    private static final String OBJECT_LAYER = "objects";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ContentFiles files;

    public WorldReader(ContentFiles files) {
        this.files = files;
    }

    public WorldRules readRules() {
        JsonNode world = parse(ContentFiles.BALANCE, files.text(ContentFiles.BALANCE)).get("world");
        return new WorldRules(world.get("stepMs").asInt(), world.get("speedTolerance").asDouble(),
                world.get("speedSlackTiles").asDouble(), world.get("interactRange").asInt());
    }

    public World readWorld() {
        JsonNode root = parse(ContentFiles.REGIONS, files.text(ContentFiles.REGIONS));
        JsonNode start = root.get("start");
        Map<String, Integer> startFlags = new LinkedHashMap<>();
        start.get("flags").properties().forEach(e -> startFlags.put(e.getKey(), e.getValue().asInt()));

        Map<String, World.Region> regions = new LinkedHashMap<>();
        for (JsonNode r : root.get("regions")) {
            List<World.LightState> lightStates = new ArrayList<>();
            for (JsonNode state : r.get("lightStates")) {
                lightStates.add(new World.LightState(state.get("id").asString(), optText(state, "flag")));
            }
            regions.put(r.get("id").asString(), new World.Region(r.get("id").asString(), r.get("name").asString(),
                    r.get("music").asString(), lightStates));
        }
        Map<String, String> mapTexts = files.maps();
        Map<String, World.Area> areas = new LinkedHashMap<>();
        Map<String, AreaMap> maps = new HashMap<>();
        for (JsonNode a : root.get("areas")) {
            String id = a.get("id").asString();
            String mapKey = optText(a, "map");
            areas.put(id, new World.Area(id, a.get("region").asString(), a.get("name").asString(), mapKey));
            if (mapKey != null) {
                String text = mapTexts.get(mapKey);
                if (text == null) {
                    throw new ContentValidationException("Area " + id + " names map " + mapKey
                            + " but " + ContentFiles.MAPS_DIR + mapKey + ContentFiles.MAP_SUFFIX + " does not exist");
                }
                maps.put(id, readMap(mapKey, text));
            }
        }
        List<World.Edge> edges = new ArrayList<>();
        for (JsonNode e : root.get("edges")) {
            edges.add(new World.Edge(e.get("from").asString(), e.get("to").asString(), e.get("weight").asInt(),
                    optText(e, "gateFlag")));
        }
        World.Start worldStart = new World.Start(start.get("area").asString(), start.get("spawn").asString(),
                start.get("dialogue").asString(), startFlags);
        return new World(worldStart, regions, areas, edges, maps);
    }

    /** One Tiled map: its size, the collision layer (any non-empty tile blocks) and the objects, in tiles. */
    AreaMap readMap(String key, String text) {
        String where = ContentFiles.MAPS_DIR + key + ContentFiles.MAP_SUFFIX;
        JsonNode map = parse(where, text);
        int width = map.get("width").asInt();
        int height = map.get("height").asInt();
        int tileSize = map.get("tilewidth").asInt();
        if (tileSize <= 0 || tileSize != map.get("tileheight").asInt()) {
            throw new ContentValidationException(where + ": tiles must be square");
        }
        BitSet blocked = new BitSet(width * height);
        List<MapObject> objects = new ArrayList<>();
        boolean hasCollision = false;
        for (JsonNode layer : map.get("layers")) {
            String name = layer.get("name").asString();
            if (COLLISION_LAYER.equals(name)) {
                hasCollision = true;
                JsonNode data = layer.get("data");
                if (data.size() != width * height) {
                    throw new ContentValidationException(where + ": collision layer has " + data.size()
                            + " tiles, expected " + width * height);
                }
                for (int i = 0; i < data.size(); i++) {
                    if (data.get(i).asLong() != 0) {
                        blocked.set(i);
                    }
                }
            } else if (OBJECT_LAYER.equals(name)) {
                for (JsonNode o : layer.get("objects")) {
                    objects.add(readObject(where, tileSize, o));
                }
            }
        }
        if (!hasCollision) {
            throw new ContentValidationException(where + ": no '" + COLLISION_LAYER + "' layer");
        }
        return new AreaMap(key, width, height, tileSize, new CollisionGrid(width, height, blocked), objects);
    }

    private MapObject readObject(String where, int tileSize, JsonNode o) {
        String name = o.get("name").asString();
        ObjectType type = ObjectType.fromTiled(o.get("type").asString()).orElseThrow(() ->
                new ContentValidationException(where + ": object " + name + " has unknown type " + o.get("type").asString()));
        int[] rect = new int[4];
        String[] fields = {"x", "y", "width", "height"};
        for (int i = 0; i < 4; i++) {
            double pixels = o.get(fields[i]).asDouble();
            if (pixels % tileSize != 0) {
                throw new ContentValidationException(where + ": object " + name + " is not aligned to the "
                        + tileSize + " px grid (" + fields[i] + " = " + pixels + ")");
            }
            rect[i] = (int) (pixels / tileSize);
        }
        Map<String, String> properties = new HashMap<>();
        JsonNode props = o.get("properties");
        if (props != null) {
            for (JsonNode p : props) {
                properties.put(p.get("name").asString(), p.get("value").asString());
            }
        }
        return new MapObject(name, type, rect[0], rect[1], Math.max(1, rect[2]), Math.max(1, rect[3]), properties);
    }

    private JsonNode parse(String fileName, String text) {
        try {
            return mapper.readTree(text);
        } catch (JacksonException e) {
            throw new ContentValidationException("Cannot read " + fileName + ": " + e.getOriginalMessage());
        }
    }

    private static String optText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
