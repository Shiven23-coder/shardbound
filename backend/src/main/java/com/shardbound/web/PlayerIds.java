package com.shardbound.web;

import com.shardbound.engine.GameState;
import org.jspecify.annotations.Nullable;

/**
 * Which player a request is about: the session's player, else the
 * {@code playerId} query parameter (title screen, before a game exists).
 * Accounts replace this with the signed-in user in V2a.
 */
final class PlayerIds {

    private PlayerIds() {
    }

    static int resolve(@Nullable GameState state, @Nullable Integer playerId) {
        if (state != null) {
            return state.playerId();
        }
        if (playerId == null) {
            throw new IllegalArgumentException("Send the X-Session header or the playerId query parameter.");
        }
        return playerId;
    }
}
