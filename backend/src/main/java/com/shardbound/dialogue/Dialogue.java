package com.shardbound.dialogue;

import com.shardbound.engine.GameState;
import com.shardbound.rules.ConditionGroup;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A conversation or text a world object starts (an NPC, a sign, a trigger)
 * or the game's opening. The first entry whose conditions hold picks the
 * first node, otherwise {@code start}.
 */
public record Dialogue(String id, @Nullable String npc, String start, List<Entry> entries) {

    public Dialogue {
        entries = List.copyOf(entries);
    }

    /** A conditional first node. */
    public record Entry(String node, ConditionGroup conditions) { }

    /** The node this dialogue opens on for this game. O(entries · conditions). */
    public String firstNode(GameState s) {
        for (Entry entry : entries) {
            if (entry.conditions().test(s)) {
                return entry.node();
            }
        }
        return start;
    }
}
