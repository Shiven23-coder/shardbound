package com.shardbound.world;

/**
 * Where the player stands: an area and a pixel position on its map (the
 * last position the server validated, §4.11), plus the facing.
 */
public record WorldPosition(String areaId, int x, int y, Facing facing) {

    /** The centre of a map object's first tile, facing down: where the player appears at a spawn point. */
    public static WorldPosition at(String areaId, AreaMap map, MapObject spawn) {
        return new WorldPosition(areaId, map.centre(spawn.x()), map.centre(spawn.y()), Facing.DOWN);
    }
}
