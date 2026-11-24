package com.shardbound.world;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The server's view of one area's Tiled map: its size, the {@code collision}
 * layer and the objects. Tile art is the client's business. Where the player
 * can walk is the layer plus every building footprint (P-43) plus the obstacles
 * active for a game (P-45): see {@link #collisionWith}.
 *
 * @param key      the map file name in {@code content/maps} without {@code .tmj}
 * @param tileSize  pixels per tile, from the map's {@code tilewidth} (P-29)
 * @param collision the {@code collision} layer as drawn, without buildings or obstacles
 */
public record AreaMap(String key, int width, int height, int tileSize, CollisionGrid collision,
                      List<MapObject> objects) {

    public AreaMap {
        objects = List.copyOf(objects);
    }

    public Optional<MapObject> object(String name) {
        return objects.stream().filter(o -> o.name().equals(name)).findFirst();
    }

    public List<MapObject> objectsOf(ObjectType type) {
        return objects.stream().filter(o -> o.type() == type).toList();
    }

    /**
     * The tiles that block for a game: the collision layer, every building
     * footprint, and every obstacle (P-45) and chest (P-65) for which {@code active} holds.
     */
    public CollisionGrid collisionWith(Predicate<MapObject> active) {
        List<MapObject> blocking = objects.stream()
                .filter(o -> o.type() == ObjectType.BUILDING
                        || ((o.type() == ObjectType.OBSTACLE || o.type() == ObjectType.CHEST) && active.test(o)))
                .toList();
        return collision.blocking(blocking);
    }

    public Optional<MapObject> spawn(String name) {
        return object(name).filter(o -> o.type() == ObjectType.SPAWN_POINT);
    }

    /** The spawn point marked {@code main}: where reachability is measured from. */
    public Optional<MapObject> mainSpawn() {
        return objectsOf(ObjectType.SPAWN_POINT).stream().filter(o -> o.flag("main")).findFirst();
    }

    /** The pixel position of a tile's centre, where the player stands on arrival. */
    public int centre(int tile) {
        return tile * tileSize + tileSize / 2;
    }

    public int tileOf(int pixel) {
        return Math.floorDiv(pixel, tileSize);
    }
}
