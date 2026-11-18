package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;

/** Adds xp and handles level-ups (D29). */
public record XpEffect(int amount) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        Rewards.grantXp(state, bus, amount);
    }
}
