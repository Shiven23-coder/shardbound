package com.shardbound.world;

/**
 * A {@code lantern} map object (P-61): a rest point with its id, its name in
 * the menu, the spawn point where a player who rested there wakes, and the
 * dialogue that holds its cold and kindling messages. MapValidator has checked
 * the properties.
 */
public record Lantern(String areaId, MapObject object, String id, String name, String spawn, String dialogue) {

    public static Lantern of(String areaId, MapObject o) {
        if (o.type() != ObjectType.LANTERN) {
            throw new IllegalArgumentException(o.name() + " is not a Lantern");
        }
        return new Lantern(areaId, o, o.property("lanternId"), o.property("name"), o.property("spawn"),
                o.property("dialogue"));
    }

    /** The flag that records this Lantern as lit (dialogue conditions read it too). */
    public String litFlag() {
        return litFlag(id);
    }

    public static String litFlag(String lanternId) {
        return "lantern_lit_" + lanternId;
    }
}
