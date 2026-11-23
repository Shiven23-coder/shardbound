package com.shardbound.item;

/**
 * A refillable flask (P-63): the Lantern Draught. The bag holds one; its
 * charges are run state, used one per drink and refilled at every rest and
 * on death. Each charge heals a share of max HP.
 */
public class Flask extends Item {

    private final int charges;
    private final int healPct;

    public Flask(String id, String name, String description, int price, int charges, int healPct) {
        super(id, name, description, price);
        this.charges = charges;
        this.healPct = healPct;
    }

    /** Charges when full. */
    public int charges() { return charges; }

    /** HP healed by one charge, as a share of max HP. */
    public int healPct() { return healPct; }

    /** HP one charge heals for a player with {@code maxHp}. */
    public int healFor(int maxHp) {
        return Math.round(maxHp * healPct / 100f);
    }

    @Override public ItemType type()      { return ItemType.FLASK; }
    @Override public boolean isStackable() { return false; }
}
