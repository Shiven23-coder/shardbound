package com.shardbound.exception;

/** The choice exists but its conditions are not met. HTTP 403. */
public class LockedChoiceException extends GameException {

    public LockedChoiceException(String label, String reason) {
        super("\"" + label + "\" is locked. " + reason);
    }

    @Override
    public String code() {
        return "LOCKED_CHOICE";
    }
}
