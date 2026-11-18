package com.shardbound.combat;

import com.shardbound.item.Weapon;
import com.shardbound.model.Stats;

/**
 * The player inside a fight. Reads and writes hp straight through to the
 * player's Stats, so damage taken in combat is the player's real hp.
 */
public class PlayerCombatant extends Combatant {

    private final String name;
    private final Stats stats;
    private final Weapon weapon;     // may be null (unarmed)

    public PlayerCombatant(String name, Stats stats, Weapon weapon) {
        this.name = name;
        this.stats = stats;
        this.weapon = weapon;
    }

    public Weapon weapon() {
        return weapon;
    }

    @Override public String name()  { return name; }
    @Override public int hp()       { return stats.hp(); }
    @Override public int maxHp()    { return stats.maxHp(); }
    @Override public int atk()      { return stats.atk(); }
    @Override public int def()      { return stats.def(); }
    @Override public int spd()      { return stats.spd(); }
    @Override protected void setHp(int hp) { stats.setHp(hp); }
    @Override public boolean isPlayer() { return true; }
}
