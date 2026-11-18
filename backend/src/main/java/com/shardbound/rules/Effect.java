package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;

/**
 * One state change caused by taking a choice. Every effect publishes an event
 * on the bus, which the session turns into a toast and achievement checks.
 * Effects are immutable content shared by every session (D20).
 */
public interface Effect {
    void apply(GameState state, EventBus bus);
}
