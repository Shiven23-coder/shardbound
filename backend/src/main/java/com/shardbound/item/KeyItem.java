package com.shardbound.item;

/** A story item (e.g. the Heartshard). Has no extra columns, so no subtype table. */
public class KeyItem extends Item {

    public KeyItem(String id, String name, String description, int price) {
        super(id, name, description, price);
    }

    @Override public ItemType type()      { return ItemType.KEY; }
    @Override public boolean isStackable() { return false; }
}
