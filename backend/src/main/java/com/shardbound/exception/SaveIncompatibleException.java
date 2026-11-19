package com.shardbound.exception;

/**
 * A stored save or run refers to content that no longer exists, or was written
 * by a newer save format than this server understands. HTTP 409.
 */
public class SaveIncompatibleException extends GameException {

    public SaveIncompatibleException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "SAVE_INCOMPATIBLE";
    }
}
