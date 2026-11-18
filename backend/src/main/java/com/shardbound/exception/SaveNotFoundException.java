package com.shardbound.exception;

/** Loading or deleting an empty save slot. HTTP 404 (D27). */
public class SaveNotFoundException extends GameException {

    public SaveNotFoundException(int playerId, int slot) {
        super("There is no save in slot " + slot + " for player " + playerId + ".");
    }

    @Override
    public String code() {
        return "SAVE_NOT_FOUND";
    }
}
