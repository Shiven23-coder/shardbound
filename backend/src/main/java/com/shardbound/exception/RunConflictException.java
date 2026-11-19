package com.shardbound.exception;

/**
 * The game changed somewhere else since this copy was loaded (another server
 * instance or a stale cache wrote a newer version). The client should refetch
 * the game. HTTP 409.
 */
public class RunConflictException extends GameException {

    public RunConflictException(String sessionId) {
        super("This game was updated elsewhere. Reload to continue.");
    }

    @Override
    public String code() {
        return "RUN_CONFLICT";
    }
}
