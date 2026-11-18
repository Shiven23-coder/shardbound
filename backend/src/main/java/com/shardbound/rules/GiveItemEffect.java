package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.item.Item;

/** Gives {@code qty} of an item (left behind with a toast if the bag is full). */
public record GiveItemEffect(Item item, int qty) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        Rewards.grantItem(state, bus, item, qty);
    }
}
