package com.shardbound.exception;

/**
 * Base class of every game-level error. Unchecked, because callers cannot
 * recover locally: the error travels up to api.ErrorHandler, which maps each
 * subclass to an HTTP status and a {@code {error, message}} body.
 */
public abstract class GameException extends RuntimeException {

    protected GameException(String message) {
        super(message);
    }

    protected GameException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Short machine-readable code sent to the client, e.g. "LOCKED_CHOICE". */
    public abstract String code();
}
