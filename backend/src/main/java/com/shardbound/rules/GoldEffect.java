package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;

/** Adds gold (negative amounts remove it, never below 0). */
public record GoldEffect(int amount) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        Rewards.grantGold(state, bus, amount);
    }
}
