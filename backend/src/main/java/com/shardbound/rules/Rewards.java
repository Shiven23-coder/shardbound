package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.exception.InventoryFullException;
import com.shardbound.item.Consumable;
import com.shardbound.item.Item;

/**
 * Shared "give the player something" helpers used by effects, quest rewards
 * and combat rewards, so the same toasts and events are produced everywhere.
 * Nothing here throws during play.
 */
public final class Rewards {

    private Rewards() {
    }

    /** Adds Shards and publishes SHARDS_GAINED (V2_SPEC §4.3). O(1). */
    public static void grantShards(GameState state, EventBus bus, int amount) {
        int change = state.stats().addShards(amount);
        if (change > 0) {
            bus.publish(GameEvent.of(EventType.SHARDS_GAINED, "+" + change + " Shards", "amount", change));
        }
    }

    /** Adds or removes gold (never below 0) and publishes GOLD_CHANGED. O(1). */
    public static void grantGold(GameState state, EventBus bus, int amount) {
        int change = state.stats().addGold(amount);
        if (change != 0) {
            String text = (change > 0 ? "+" : "") + change + " gold";
            bus.publish(GameEvent.of(EventType.GOLD_CHANGED, text, "amount", change));
        }
    }

    /**
     * Uses one consumable: heals, restores FP, removes it from the bag and
     * publishes HEALED with a summary toast. Time O(1) average.
     */
    public static void useConsumable(GameState state, EventBus bus, Consumable consumable) {
        int healed = state.stats().heal(consumable.heal());
        int restored = state.stats().restoreFp(consumable.fp());
        state.inventory().remove(consumable.id(), 1);
        StringBuilder text = new StringBuilder("Used " + consumable.name());
        if (healed > 0) {
            text.append(": +").append(healed).append(" HP");
        }
        if (restored > 0) {
            text.append(healed > 0 ? ", +" : ": +").append(restored).append(" FP");
        }
        bus.publish(GameEvent.of(EventType.HEALED, text.toString(), "itemId", consumable.id()));
    }

    /**
     * Gives an item. If the bag is full the item is left behind with a toast
     * instead of throwing, so a half-applied choice can never happen.
     * Owning a non-stackable item twice is impossible; that case is silent.
     * Time O(1) average.
     */
    public static void grantItem(GameState state, EventBus bus, Item item, int qty) {
        try {
            if (state.inventory().add(item, qty)) {
                String text = qty > 1 && item.isStackable()
                        ? "Obtained: " + item.name() + " × " + qty
                        : "Obtained: " + item.name();
                bus.publish(GameEvent.of(EventType.ITEM_GAINED, text, "itemId", item.id()));
            }
        } catch (InventoryFullException full) {
            bus.publish(GameEvent.of(EventType.ITEM_LOST,
                    "No room for " + item.name() + " — it was left behind", "itemId", item.id()));
        }
    }
}
