package com.shardbound.event;

import java.util.HashMap;
import java.util.Map;

/**
 * Something that happened, plus details. If {@code data} contains a
 * "message" entry, the session's toast listener shows it to the player.
 */
public record GameEvent(EventType type, Map<String, Object> data) {

    public static final String MESSAGE = "message";

    public GameEvent {
        data = Map.copyOf(data);
    }

    /** An event with only a toast message. O(1). */
    public static GameEvent of(EventType type, String message) {
        return new GameEvent(type, Map.of(MESSAGE, message));
    }

    /** An event with a toast message and one detail entry. O(1). */
    public static GameEvent of(EventType type, String message, String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(MESSAGE, message);
        data.put(key, value);
        return new GameEvent(type, data);
    }

    /** The toast text, or null if this event should not produce a toast. */
    public String message() {
        Object value = data.get(MESSAGE);
        return value == null ? null : value.toString();
    }

    /** A detail value as a String, or null. */
    public String text(String key) {
        Object value = data.get(key);
        return value == null ? null : value.toString();
    }

    /** A detail value as an int, or the fallback. */
    public int number(String key, int fallback) {
        Object value = data.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }
}
