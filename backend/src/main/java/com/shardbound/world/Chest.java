package com.shardbound.world;

import org.jspecify.annotations.Nullable;

/**
 * A {@code chest} map object (P-65): what it holds, the flag that records it as opened, and the fallback
 * given instead when the player already owns an item that does not stack (Shards of one creep of
 * {@code fallbackLevel}, or {@code fallbackItem} × {@code fallbackQty}). MapValidator has checked the
 * properties.
 */
public record Chest(String areaId, MapObject object, String item, int qty, String flag,
                    @Nullable Integer fallbackLevel, @Nullable String fallbackItem, int fallbackQty) {

    public static Chest of(String areaId, MapObject o) {
        if (o.type() != ObjectType.CHEST) {
            throw new IllegalArgumentException(o.name() + " is not a chest");
        }
        String level = o.property("fallbackLevel");
        String fallbackQty = o.property("fallbackQty");
        return new Chest(areaId, o, o.property("item"), Integer.parseInt(o.property("qty")), o.property("flag"),
                level == null ? null : Integer.valueOf(level), o.property("fallbackItem"),
                fallbackQty == null ? 1 : Integer.parseInt(fallbackQty));
    }

    public String id() {
        return object.name();
    }
}
