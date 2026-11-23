package com.shardbound.engine;

/**
 * Shards dropped by a death (V2_SPEC §4.4, P-62): how many, and the tile in
 * an area where the battle started. At most one drop exists at a time.
 */
public record LostShards(String areaId, int tileX, int tileY, int amount) { }
