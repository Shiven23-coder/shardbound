package com.shardbound.item;

import com.shardbound.model.Tier;

/** A weapon: tier multiplier plus flat attack and a crit chance. */
public class Weapon extends Item {

    private final Tier tier;
    private final int attack;
    private final int critPct;
    private final String ability;   // may be null

    public Weapon(String id, String name, String description, int price,
                  Tier tier, int attack, int critPct, String ability) {
        super(id, name, description, price);
        this.tier = tier;
        this.attack = attack;
        this.critPct = critPct;
        this.ability = ability;
    }

    public Tier tier()      { return tier; }
    public int attack()     { return attack; }
    public int critPct()    { return critPct; }
    public String ability() { return ability; }

    @Override public ItemType type()      { return ItemType.WEAPON; }
    @Override public boolean isStackable() { return false; }
}
