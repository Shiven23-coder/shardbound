package com.shardbound.exception;

/** A new item would need a 21st inventory slot. HTTP 409. */
public class InventoryFullException extends GameException {

    public InventoryFullException(String itemName, int capacity) {
        super("No room for " + itemName + ": the inventory is full (" + capacity + " slots).");
    }

    @Override
    public String code() {
        return "INVENTORY_FULL";
    }
}
