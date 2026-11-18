package com.shardbound.item;

/**
 * An item plus how many of it are held in one inventory slot.
 * Generic so code that knows the stack holds a {@code Weapon} can get a
 * {@code Weapon} back without casting.
 *
 * @param <T> the item type held in this stack
 */
public class ItemStack<T extends Item> {

    private final T item;
    private int qty;

    public ItemStack(T item, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Stack quantity must be positive: " + qty);
        }
        this.item = item;
        this.qty = qty;
    }

    public T item()  { return item; }
    public int qty() { return qty; }

    /** Adds n to the stack. O(1). */
    public void add(int n) {
        qty += n;
    }

    /** Removes up to n; returns how many were actually removed. O(1). */
    public int remove(int n) {
        int removed = Math.min(n, qty);
        qty -= removed;
        return removed;
    }

    public boolean isEmpty() {
        return qty <= 0;
    }

    /** Independent copy sharing the same immutable item. O(1). */
    public ItemStack<T> copy() {
        return new ItemStack<>(item, qty);
    }
}
