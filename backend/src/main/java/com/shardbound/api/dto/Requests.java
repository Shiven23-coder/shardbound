package com.shardbound.api.dto;

/** JSON request bodies (Jackson maps them onto these records). */
public final class Requests {

    private Requests() {
    }

    public record ProfileRequest(String username) { }

    public record NewGameRequest(int playerId, boolean demoMode) { }

    public record ChooseRequest(int choiceId) { }

    /** type is ATTACK, DEFEND, SKILL, ITEM or FLEE. */
    public record CombatRequest(String type, int target, String itemId) { }

    public record ItemRequest(String itemId) { }
}
