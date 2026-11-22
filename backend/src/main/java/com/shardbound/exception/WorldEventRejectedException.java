package com.shardbound.exception;

import com.shardbound.world.WorldPosition;

/**
 * A world event the server cannot accept (§4.11): wrong mode, a blocked or
 * unreachable tile, a walk faster than possible, or a target out of range.
 * Carries the last validated position, which the client moves the player
 * back to. HTTP 409.
 */
public class WorldEventRejectedException extends GameException {

    private final transient WorldPosition lastPosition;

    public WorldEventRejectedException(String message, WorldPosition lastPosition) {
        super(message);
        this.lastPosition = lastPosition;
    }

    public WorldPosition lastPosition() {
        return lastPosition;
    }

    @Override
    public String code() {
        return "WORLD_EVENT_REJECTED";
    }
}
