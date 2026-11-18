package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;

import java.util.Map;

/** flags[key] = value. Flags are hidden story state, so the event carries no toast message. */
public record SetFlagEffect(String key, int value) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        state.setFlag(key, value);
        bus.publish(new GameEvent(EventType.FLAG_SET, Map.of("key", key, "value", value)));
    }
}
