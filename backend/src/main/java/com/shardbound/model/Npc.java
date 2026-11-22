package com.shardbound.model;

/** An immutable NPC definition. {@code faction} may be null. */
/** An NPC; {@code shortName} is how story cues name them ("Corvin"). */
public record Npc(String id, String name, String shortName, Faction faction) {
}
