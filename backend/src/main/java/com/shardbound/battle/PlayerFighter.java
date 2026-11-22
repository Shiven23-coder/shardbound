package com.shardbound.battle;

import com.shardbound.item.Weapon;
import com.shardbound.model.Stats;
import com.shardbound.progression.PlayerStats;
import org.jspecify.annotations.Nullable;

/**
 * The player in battle: HP written straight through to {@link Stats}, every
 * battle number derived from attributes and the equipped weapon (V2_SPEC
 * §4.3, §4.6). The player counts as Human in the element chart.
 */
public final class PlayerFighter extends Fighter {

    public static final String SPRITE = "player";

    private final Stats stats;
    private final @Nullable Weapon weapon;

    public PlayerFighter(Stats stats, @Nullable Weapon weapon) {
        this.stats = stats;
        this.weapon = weapon;
    }

    private PlayerStats derived() {
        return stats.derived();
    }

    public Stats stats()               { return stats; }
    public @Nullable Weapon weapon()   { return weapon; }

    @Override public String name()        { return "You"; }
    @Override public int level()          { return stats.level(); }
    @Override public Family family()      { return Family.HUMAN; }
    @Override public String sprite()      { return SPRITE; }
    @Override public int hp()             { return stats.hp(); }
    @Override public int maxHp()          { return stats.maxHp(); }
    @Override public boolean isPlayer()   { return true; }
    @Override protected int baseDefence() { return derived().defence(); }
    @Override protected int baseSpeed()   { return derived().speed(); }
    @Override public int critPct()        { return derived().critPct(weapon); }
    @Override public int statusBonusPct() { return derived().arcaneStatusPct(); }
    @Override public int statusResistPct() { return derived().statusResistPct(); }
    @Override public int takeDamage(int amount) { return stats.damage(amount); }

    @Override
    public double raw(Skill move) {
        double power = switch (move.scaling()) {
            case WEAPON -> derived().attackRating(weapon);
            case MIND -> derived().mindPower();
            case ARCANE -> derived().arcanePower();
        };
        return power * move.power();
    }
}
