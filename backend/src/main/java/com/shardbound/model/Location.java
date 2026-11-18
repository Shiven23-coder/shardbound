package com.shardbound.model;

/** An immutable location. {@code region} drives the UI theme (VILLAGE, FOREST, DUNGEON, KINGDOM, SPIRE). */
public record Location(String id, String name, String region) {
}
