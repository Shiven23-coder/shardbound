package com.shardbound.item;

/** A one-use item that restores hp and/or energy. */
public class Consumable extends Item {

    private final int heal;
    private final int energy;

    public Consumable(String id, String name, String description, int price, int heal, int energy) {
        super(id, name, description, price);
        this.heal = heal;
        this.energy = energy;
    }

    public int heal()   { return heal; }
    public int energy() { return energy; }

    @Override public ItemType type()      { return ItemType.CONSUMABLE; }
    @Override public boolean isStackable() { return true; }
}
