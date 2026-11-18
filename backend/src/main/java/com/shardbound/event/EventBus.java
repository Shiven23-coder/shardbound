package com.shardbound.event;

import com.shardbound.engine.GameState;

import java.util.ArrayList;
import java.util.List;

/**
 * Observer pattern "subject". One bus per session: it knows its GameState,
 * so {@code publish(event)} keeps the spec's one-argument signature while
 * listeners still receive the state. Not shared between threads; every call
 * happens under the session's lock (D16).
 */
public class EventBus {

    private final GameState owner;
    private final List<GameListener> listeners = new ArrayList<>();

    public EventBus(GameState owner) {
        this.owner = owner;
    }

    public void subscribe(GameListener listener) {
        listeners.add(listener);
    }

    /** Delivers the event to every listener in subscription order. Time O(L), space O(1). */
    public void publish(GameEvent event) {
        for (GameListener listener : listeners) {
            listener.onEvent(event, owner);
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
