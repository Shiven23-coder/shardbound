package com.shardbound.engine;

import com.shardbound.content.GameContent;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.exception.InventoryFullException;
import com.shardbound.item.Item;
import com.shardbound.item.Weapon;
import com.shardbound.progression.PlayerStats;
import com.shardbound.world.Chest;

import java.util.Map;

/**
 * Opening a chest (P-65), after GameEngine has validated the world event. A chest is never empty: if the
 * player already owns an item that does not stack, the chest gives its fallback instead. It is never lost
 * either: if the bag has no room, nothing changes and the chest stays closed. One LOOT toast says what
 * was found.
 */
final class Chests {

    private Chests() {
    }

    static void open(GameState s, Chest chest, GameContent content) {
        Item item = content.items().get(chest.item());
        boolean owned = !item.isStackable() && s.inventory().has(item.id());
        String found;
        if (!owned) {
            if (!take(s, item, chest.qty())) {
                return;
            }
            found = "Found: " + amount(item, chest.qty());
        } else if (chest.fallbackItem() != null) {
            Item fallback = content.items().get(chest.fallbackItem());
            if (!take(s, fallback, chest.fallbackQty())) {
                return;
            }
            found = "You already carry " + article(item) + ". You take " + amount(fallback, chest.fallbackQty()) + " instead.";
        } else {
            int shards = content.battle().rules().creepShards(chest.fallbackLevel());
            s.stats().addShards(shards);
            s.bus().publish(new GameEvent(EventType.SHARDS_GAINED, Map.of("amount", shards)));
            found = "You already carry " + article(item) + ". You take " + shards + " Shards instead.";
        }
        s.setFlag(chest.flag(), 1);
        s.bus().publish(new GameEvent(EventType.CHEST_OPENED, Map.of("chestId", chest.id(), "itemId", item.id())));
        Weapon weapon = item instanceof Weapon w && !owned ? w : null;
        s.addToast(new Toast("LOOT", found, weapon == null ? null : weapon.tier().name(), owned ? null : item.name(),
                weapon != null && beatsTheEquipped(s, weapon)));
    }

    /** True when {@code weapon} would give a higher attack rating than the one equipped (V2_SPEC §4.6). */
    private static boolean beatsTheEquipped(GameState s, Weapon weapon) {
        PlayerStats stats = s.stats().derived();
        return stats.attackRating(weapon) > stats.attackRating(s.inventory().equippedWeapon());
    }

    /** Puts {@code qty} of an item in the bag (ITEM_GAINED, without a toast of its own); false if there is no room. */
    private static boolean take(GameState s, Item item, int qty) {
        try {
            s.inventory().add(item, qty);
        } catch (InventoryFullException full) {
            s.addToast(new Toast("INFO", "Your bag is full."));
            return false;
        }
        s.bus().publish(new GameEvent(EventType.ITEM_GAINED, Map.of("itemId", item.id())));
        return true;
    }

    private static String amount(Item item, int qty) {
        return qty > 1 ? item.name() + " × " + qty : item.name();
    }

    private static String article(Item item) {
        return ("AEIOU".indexOf(item.name().charAt(0)) >= 0 ? "an " : "a ") + item.name();
    }
}
