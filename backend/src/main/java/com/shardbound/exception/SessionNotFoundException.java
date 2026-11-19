package com.shardbound.exception;

/** The X-Session header is missing or names no game. HTTP 404. */
public class SessionNotFoundException extends GameException {

    public SessionNotFoundException() {
        super("Unknown or missing session. Start a new game first.");
    }

    @Override
    public String code() {
        return "SESSION_NOT_FOUND";
    }
}
