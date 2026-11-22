package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;

/**
 * Adds to NPC affinity, clamped to −100…100. The toast is a story cue
 * ("Corvin will remember that."), not a number; the number shows in the Bag.
 */
public record AffinityEffect(String npcId, String warmerCue, String colderCue, int delta) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        int change = state.addAffinity(npcId, delta);
        if (change == 0) {
            return;     // already at the limit: nothing to remember
        }
        bus.publish(GameEvent.of(EventType.AFFINITY_CHANGED, change > 0 ? warmerCue : colderCue, "npc", npcId));
    }
}
