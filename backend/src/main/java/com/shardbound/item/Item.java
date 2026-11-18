package com.shardbound.item;

/**
 * Base class of the item hierarchy (mirrors the item / weapon / consumable
 * ISA tables). Items are immutable content shared by every session (D20);
 * how many a player owns lives in their {@link Inventory}, not here.
 */
public abstract class Item {

    protected final String id;
    protected final String name;
    protected final String description;
    protected final int price;

    protected Item(String id, String name, String description, int price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public String id()          { return id; }
    public String name()        { return name; }
    public String description() { return description; }
    public int price()          { return price; }

    /** Which subtype table this item belongs to. */
    public abstract ItemType type();

    /** Consumables stack in one inventory slot; weapons and key items do not (spec §6.4). */
    public abstract boolean isStackable();

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
