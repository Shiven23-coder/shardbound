package com.shardbound.world;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * One object of a map's {@code objects} layer, in tiles. Its name is its id
 * within the area. Properties are kept as text; typed accessors read them.
 */
public record MapObject(String name, ObjectType type, int x, int y, int width, int height,
                        Map<String, String> properties) {

    public MapObject {
        properties = Map.copyOf(properties);
    }

    public @Nullable String property(String key) {
        return properties.get(key);
    }

    public boolean flag(String key) {
        return Boolean.parseBoolean(properties.get(key));
    }

    /** True if the tile lies on this object's rectangle. */
    public boolean covers(int tileX, int tileY) {
        return tileX >= x && tileX < x + width && tileY >= y && tileY < y + height;
    }

    /** Chebyshev distance in tiles from a tile to the nearest tile of this object (0 when on it). */
    public int distanceTo(int tileX, int tileY) {
        int dx = Math.max(Math.max(x - tileX, tileX - (x + width - 1)), 0);
        int dy = Math.max(Math.max(y - tileY, tileY - (y + height - 1)), 0);
        return Math.max(dx, dy);
    }
}
