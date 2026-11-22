package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;

/** Gives Shards (V2_SPEC §4.3); replaces v1's XP effect (P-55). */
public record ShardsEffect(int amount) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        Rewards.grantShards(state, bus, amount);
    }
}
