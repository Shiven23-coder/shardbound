package com.shardbound.exception;

/** The choice id does not exist on the current node. HTTP 400. */
public class InvalidChoiceException extends GameException {

    public InvalidChoiceException(int choiceId) {
        super("Choice " + choiceId + " is not available on this page.");
    }

    @Override
    public String code() {
        return "INVALID_CHOICE";
    }
}
