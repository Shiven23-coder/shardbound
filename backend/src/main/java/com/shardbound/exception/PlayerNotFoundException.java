package com.shardbound.exception;

/** No player profile with the given id. HTTP 404. */
public class PlayerNotFoundException extends GameException {

    public PlayerNotFoundException(int playerId) {
        super("No player with id " + playerId + ".");
    }

    @Override
    public String code() {
        return "PLAYER_NOT_FOUND";
    }
}
