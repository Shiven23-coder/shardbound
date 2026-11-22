package com.shardbound.item;

import com.shardbound.model.Tier;
import org.jspecify.annotations.Nullable;

/**
 * A weapon (V2_SPEC §4.6): a tier, a base attack, scaling grades for
 * Strength, Dexterity and Arcane (S … E, or null for none) and a crit chance.
 * The attack rating is computed by {@code progression.PlayerStats}.
 */
public class Weapon extends Item {

    private final Tier tier;
    private final int base;
    private final int critPct;
    private final @Nullable String strGrade;
    private final @Nullable String dexGrade;
    private final @Nullable String arcGrade;
    private final @Nullable String ability;

    public Weapon(String id, String name, String description, int price, Tier tier, int base, int critPct,
                  @Nullable String strGrade, @Nullable String dexGrade, @Nullable String arcGrade,
                  @Nullable String ability) {
        super(id, name, description, price);
        this.tier = tier;
        this.base = base;
        this.critPct = critPct;
        this.strGrade = strGrade;
        this.dexGrade = dexGrade;
        this.arcGrade = arcGrade;
        this.ability = ability;
    }

    public Tier tier()                  { return tier; }
    public int base()                   { return base; }
    public int critPct()                { return critPct; }
    public @Nullable String strGrade()  { return strGrade; }
    public @Nullable String dexGrade()  { return dexGrade; }
    public @Nullable String arcGrade()  { return arcGrade; }
    public @Nullable String ability()   { return ability; }

    @Override public ItemType type()      { return ItemType.WEAPON; }
    @Override public boolean isStackable() { return false; }
}
