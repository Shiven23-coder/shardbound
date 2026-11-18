package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;

/** Adds to NPC affinity, clamped to −100…100. */
public record AffinityEffect(String npcId, String npcName, int delta) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        int change = state.addAffinity(npcId, delta);
        String text = RepEffect.signed(change) + " affinity with " + npcName;
        bus.publish(GameEvent.of(EventType.AFFINITY_CHANGED, text, "npc", npcId));
    }
}
