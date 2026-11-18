package com.shardbound.event;

import com.shardbound.engine.GameState;

/** Observer interface: receives every event published on a session's bus. */
@FunctionalInterface
public interface GameListener {
    void onEvent(GameEvent event, GameState state);
}
