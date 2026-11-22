package com.shardbound.item;

/** A one-use item that restores HP and/or FP. */
public class Consumable extends Item {

    private final int heal;
    private final int fp;

    public Consumable(String id, String name, String description, int price, int heal, int fp) {
        super(id, name, description, price);
        this.heal = heal;
        this.fp = fp;
    }

    public int heal()   { return heal; }
    public int fp() { return fp; }

    @Override public ItemType type()      { return ItemType.CONSUMABLE; }
    @Override public boolean isStackable() { return true; }
}
