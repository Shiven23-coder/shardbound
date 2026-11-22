package com.shardbound.item;

import com.shardbound.exception.InventoryFullException;
import com.shardbound.model.Tier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's bag: at most {@link #CAPACITY} slots.
 *
 * <p>Backed by a {@code LinkedHashMap<String, ItemStack>} keyed by item id:
 * O(1) add/remove/lookup like a HashMap, but iteration follows the order items
 * were obtained, so the UI list is stable between requests.
 *
 * <p>Rules (spec §6.4): consumables stack in one slot; a weapon or key item
 * occupies its own slot and is owned at most once (the save table's primary
 * key is (save_id, item_id)); exactly zero or one weapon is equipped.
 */
public class Inventory {

    public static final int CAPACITY = 20;

    /** Tier descending, then base attack descending (spec §6.4). */
    public static final Comparator<Weapon> BY_TIER_THEN_ATTACK =
            Comparator.comparing(Weapon::tier, Tier.BY_RANK_DESC)
                      .thenComparing(Comparator.comparingInt(Weapon::base).reversed());

    private final LinkedHashMap<String, ItemStack<Item>> stacks = new LinkedHashMap<>();
    private String equippedWeaponId;   // null = nothing equipped

    /**
     * Adds {@code qty} of an item. Returns true if anything was added; false
     * when a non-stackable item is already owned (it cannot be held twice).
     * Throws InventoryFullException if a NEW slot is needed and all 20 are used.
     * Time O(1) average, space O(1).
     */
    public boolean add(Item item, int qty) {
        if (qty <= 0) {
            return false;
        }
        ItemStack<Item> existing = stacks.get(item.id());
        if (existing != null) {
            if (!item.isStackable()) {
                return false;
            }
            existing.add(qty);
            return true;
        }
        if (stacks.size() >= CAPACITY) {
            throw new InventoryFullException(item.name(), CAPACITY);
        }
        int amount = item.isStackable() ? qty : 1;
        stacks.put(item.id(), new ItemStack<>(item, amount));
        return true;
    }

    /**
     * Removes up to {@code qty}; returns how many were removed. Removing the
     * last copy of the equipped weapon unequips it. Time O(1) average.
     */
    public int remove(String itemId, int qty) {
        ItemStack<Item> stack = stacks.get(itemId);
        if (stack == null || qty <= 0) {
            return 0;
        }
        int removed = stack.remove(qty);
        if (stack.isEmpty()) {
            stacks.remove(itemId);
            if (itemId.equals(equippedWeaponId)) {
                equippedWeaponId = null;
            }
        }
        return removed;
    }

    /** Quantity owned (0 if none). O(1) average. */
    public int count(String itemId) {
        ItemStack<Item> stack = stacks.get(itemId);
        return stack == null ? 0 : stack.qty();
    }

    /** True if the item is owned. O(1) average. */
    public boolean has(String itemId) {
        return stacks.containsKey(itemId);
    }

    /** Item object for an owned id, or null. O(1) average. */
    public Item get(String itemId) {
        ItemStack<Item> stack = stacks.get(itemId);
        return stack == null ? null : stack.item();
    }

    /**
     * Equips an owned weapon. Returns the weapon, or null if the id is not an
     * owned weapon (the caller turns that into a 409). O(1) average.
     */
    public Weapon equip(String itemId) {
        Item item = get(itemId);
        if (!(item instanceof Weapon weapon)) {
            return null;
        }
        equippedWeaponId = itemId;
        return weapon;
    }

    /** The equipped weapon, or null. O(1) average. */
    public Weapon equippedWeapon() {
        if (equippedWeaponId == null) {
            return null;
        }
        return (Weapon) get(equippedWeaponId);
    }

    public String equippedWeaponId() {
        return equippedWeaponId;
    }

    /** Owned weapons, best first (tier desc, then attack desc). Time O(n log n), space O(n). */
    public List<Weapon> weaponsByTier() {
        List<Weapon> weapons = new ArrayList<>();
        for (ItemStack<Item> stack : stacks.values()) {
            if (stack.item() instanceof Weapon weapon) {
                weapons.add(weapon);
            }
        }
        weapons.sort(BY_TIER_THEN_ATTACK);
        return weapons;
    }

    /** Number of weapons owned. O(n). */
    public int weaponCount() {
        int count = 0;
        for (ItemStack<Item> stack : stacks.values()) {
            if (stack.item() instanceof Weapon) {
                count++;
            }
        }
        return count;
    }

    public int usedSlots() {
        return stacks.size();
    }

    /** Read-only view of the stacks in insertion order. O(1). */
    public Collection<ItemStack<Item>> stacksView() {
        return Collections.unmodifiableCollection(stacks.values());
    }

    /** Read-only id → quantity map (used when saving). O(n). */
    public Map<String, Integer> quantities() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, ItemStack<Item>> entry : stacks.entrySet()) {
            result.put(entry.getKey(), entry.getValue().qty());
        }
        return Collections.unmodifiableMap(result);
    }

    /** Independent copy: new stacks, same immutable items. Time O(n), space O(n). */
    public Inventory copy() {
        Inventory copy = new Inventory();
        for (Map.Entry<String, ItemStack<Item>> entry : stacks.entrySet()) {
            copy.stacks.put(entry.getKey(), entry.getValue().copy());
        }
        copy.equippedWeaponId = equippedWeaponId;
        return copy;
    }
}
