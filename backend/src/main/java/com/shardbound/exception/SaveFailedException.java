package com.shardbound.exception;

/** A save transaction was rolled back or the database is unreachable. HTTP 500. */
public class SaveFailedException extends GameException {

    public SaveFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "SAVE_FAILED";
    }
}
