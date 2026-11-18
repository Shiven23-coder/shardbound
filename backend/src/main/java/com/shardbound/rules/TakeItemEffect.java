package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.item.Item;

/** Removes up to {@code qty} of an item; does nothing if none is owned. */
public record TakeItemEffect(Item item, int qty) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        int removed = state.inventory().remove(item.id(), qty);
        if (removed > 0) {
            bus.publish(GameEvent.of(EventType.ITEM_LOST, "Lost: " + item.name(), "itemId", item.id()));
        }
    }
}
