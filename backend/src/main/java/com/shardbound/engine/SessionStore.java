package com.shardbound.engine;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All live games, keyed by the session UUID that the client sends in the
 * {@code X-Session} header. {@code ConcurrentHashMap} makes the MAP safe to
 * use from many Jetty threads at once; each GameState is additionally locked
 * by GameEngine while a request uses it (D16).
 */
public class SessionStore {

    private final ConcurrentHashMap<String, GameState> sessions = new ConcurrentHashMap<>();

    /** A fresh random session id. O(1). */
    public static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    /** Stores (or replaces, e.g. after a load) the state under its session id. O(1) average. */
    public void put(GameState state) {
        sessions.put(state.sessionId(), state);
    }

    /** The state for a session id, or null if unknown. O(1) average. */
    public GameState get(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public int size() {
        return sessions.size();
    }
}
