package com.shardbound.battle;

/**
 * A dialogue fight from world.json (P-37): one enemy at a fixed level, and
 * whether the player may run.
 */
public record Encounter(String id, String name, boolean canFlee, String creepId, int level) { }
