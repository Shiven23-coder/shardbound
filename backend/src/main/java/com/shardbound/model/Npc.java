package com.shardbound.model;

/** An immutable NPC definition. {@code faction} may be null. */
public record Npc(String id, String name, Faction faction, String locationId) {
}
