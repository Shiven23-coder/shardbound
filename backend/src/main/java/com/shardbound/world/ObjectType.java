package com.shardbound.world;

import java.util.Locale;
import java.util.Optional;

/**
 * The object types of a map's {@code objects} layer (V2_SPEC §9). A
 * {@code BUILDING} is a building's footprint, which always blocks (P-43); an
 * {@code OBSTACLE} closes a path and blocks while it is active (P-45); a
 * {@code CREEP_SPAWN} is where a creep stands and starts battles (V2_SPEC §4.2); a {@code CHEST}
 * opens once per run (P-65); a {@code BOSS} is where a boss stands until it falls (P-67).
 */
public enum ObjectType {
    SPAWN_POINT, EXIT, NPC, INTERACT, TRIGGER, OBSTACLE, BUILDING, LIGHT, EFFECT, PROP, CREEP_SPAWN, LANTERN, CHEST, BOSS, RESERVED;

    /** The type for a Tiled object type such as {@code "spawn_point"}; empty if unknown. */
    public static Optional<ObjectType> fromTiled(String type) {
        try {
            return Optional.of(valueOf(type.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** True for the objects the player can act on through a world event. */
    public boolean interactive() {
        return this == NPC || this == INTERACT || this == TRIGGER || this == OBSTACLE || this == LANTERN
                || this == CHEST;
    }
}
