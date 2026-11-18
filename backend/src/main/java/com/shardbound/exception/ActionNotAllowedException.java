package com.shardbound.exception;

/**
 * A well-formed request that the current game mode forbids, e.g. saving in
 * combat or choosing a story option mid-fight. HTTP 409 Conflict (D27).
 */
public class ActionNotAllowedException extends GameException {

    public ActionNotAllowedException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "ACTION_NOT_ALLOWED";
    }
}
