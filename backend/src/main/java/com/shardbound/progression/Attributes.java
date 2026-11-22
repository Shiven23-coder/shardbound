package com.shardbound.progression;

import java.util.EnumMap;
import java.util.Map;

/**
 * A player's six attributes. Level is derived from them (V2_SPEC §4.3):
 * {@code 1 + sum − 6 · start}, so a new character is level 1.
 */
public final class Attributes {

    private final EnumMap<Attribute, Integer> values;
    private final int start;

    private Attributes(EnumMap<Attribute, Integer> values, int start) {
        this.values = values;
        this.start = start;
    }

    /** Every attribute at {@code start}. */
    public static Attributes starting(int start) {
        EnumMap<Attribute, Integer> values = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) {
            values.put(a, start);
        }
        return new Attributes(values, start);
    }

    /** Stored values (a save); attributes missing from the map take the start value. */
    public static Attributes of(Map<Attribute, Integer> stored, int start) {
        Attributes attributes = starting(start);
        stored.forEach(attributes.values::put);
        return attributes;
    }

    public int get(Attribute attribute) {
        return values.get(attribute);
    }

    public int level() {
        int sum = 0;
        for (int value : values.values()) {
            sum += value;
        }
        return 1 + sum - Attribute.values().length * start;
    }

    public Map<Attribute, Integer> asMap() {
        return new EnumMap<>(values);
    }

    public Attributes copy() {
        return new Attributes(new EnumMap<>(values), start);
    }
}
