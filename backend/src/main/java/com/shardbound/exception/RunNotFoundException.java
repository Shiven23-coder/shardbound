package com.shardbound.exception;

/**
 * The X-Session header is missing or names no game in progress. HTTP 404,
 * code RUN_NOT_FOUND: the only answer that makes the client drop its session (P-30).
 */
public class RunNotFoundException extends GameException {

    public RunNotFoundException() {
        super("Unknown or missing session. Start a new game first.");
    }

    @Override
    public String code() {
        return "RUN_NOT_FOUND";
    }
}
