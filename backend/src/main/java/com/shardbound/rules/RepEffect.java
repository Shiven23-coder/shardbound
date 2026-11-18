package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.model.Faction;

/** Adds to faction reputation, clamped to −100…100. */
public record RepEffect(Faction faction, int delta) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        int change = state.addReputation(faction, delta);
        String text = signed(change) + " " + faction.displayName() + " reputation";
        bus.publish(GameEvent.of(EventType.REP_CHANGED, text, "faction", faction.name()));
    }

    static String signed(int n) {
        return n >= 0 ? "+" + n : String.valueOf(n);
    }
}
