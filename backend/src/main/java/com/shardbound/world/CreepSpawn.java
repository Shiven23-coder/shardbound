package com.shardbound.world;

/**
 * A {@code creep_spawn} map object read as numbers (V2_SPEC §4.2): which
 * enemy stands there, its level range and how close the player may come
 * before it attacks. MapValidator has checked the properties.
 */
public record CreepSpawn(MapObject object, String template, int levelMin, int levelMax, int aggroRadius) {

    public static CreepSpawn of(MapObject o) {
        if (o.type() != ObjectType.CREEP_SPAWN) {
            throw new IllegalArgumentException(o.name() + " is not a creep spawn");
        }
        return new CreepSpawn(o, o.property("template"), Integer.parseInt(o.property("levelMin")),
                Integer.parseInt(o.property("levelMax")), Integer.parseInt(o.property("aggroRadius")));
    }

    public String name() {
        return object.name();
    }

    /** Chebyshev distance in tiles from a tile to the creep. */
    public int distanceTo(int tileX, int tileY) {
        return object.distanceTo(tileX, tileY);
    }
}
