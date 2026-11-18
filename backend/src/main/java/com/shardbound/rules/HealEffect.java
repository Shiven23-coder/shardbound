package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;

/** +amount hp, clamped to max hp. */
public record HealEffect(int amount) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        int healed = state.stats().heal(amount);
        if (healed > 0) {
            bus.publish(GameEvent.of(EventType.HEALED, "+" + healed + " HP", "amount", healed));
        }
    }
}
