package com.shardbound.api;

import com.shardbound.engine.GameState;
import com.shardbound.engine.SessionStore;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

/** Small helpers shared by the controllers for reading the session and player. */
final class Sessions {

    static final String HEADER = "X-Session";

    private Sessions() {
    }

    /** The caller's GameState, or 404 if the X-Session header is missing/unknown. */
    static GameState require(Context ctx, SessionStore store) {
        GameState state = store.get(ctx.header(HEADER));
        if (state == null) {
            throw new NotFoundResponse("Unknown or missing session. Start a new game first.");
        }
        return state;
    }

    /** The caller's GameState if the header names a live session, otherwise null. */
    static GameState optional(Context ctx, SessionStore store) {
        return store.get(ctx.header(HEADER));
    }

    /** Player id from the session if there is one, else from the ?playerId= query parameter. */
    static int playerId(Context ctx, SessionStore store) {
        GameState state = optional(ctx, store);
        if (state != null) {
            return state.playerId();
        }
        String param = ctx.queryParam("playerId");
        if (param == null) {
            throw new BadRequestResponse("playerId query parameter or X-Session header required");
        }
        return parseInt(param, "playerId");
    }

    static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse(name + " must be a number");
        }
    }
}
