package com.shardbound.model;

import com.shardbound.progression.Attributes;
import com.shardbound.progression.PlayerStats;
import com.shardbound.progression.ProgressionRules;

/**
 * The player's numbers. Stored (and saved): hp, fp, gold, Shards and the six
 * attributes. Everything else (max HP, max FP, level, defence, speed) is
 * derived from the attributes by {@link PlayerStats} (V2_SPEC §4.3), so a save
 * can never hold a maximum that disagrees with them.
 */
public class Stats {

    private final Attributes attributes;
    private final PlayerStats derived;
    private int hp;
    private int fp;
    private int gold;
    private int shards;

    public Stats(Attributes attributes, ProgressionRules rules, int hp, int fp, int gold, int shards) {
        this.attributes = attributes;
        this.derived = new PlayerStats(attributes, rules);
        this.hp = clamp(hp, 0, maxHp());
        this.fp = clamp(fp, 0, maxFp());
        this.gold = Math.max(0, gold);
        this.shards = Math.max(0, shards);
    }

    /** A new character: every attribute at its start value, full HP and FP, the starting gold. */
    public static Stats starting(ProgressionRules rules, int gold) {
        Attributes attributes = Attributes.starting(rules.attributeStart());
        PlayerStats derived = new PlayerStats(attributes, rules);
        return new Stats(attributes, rules, derived.maxHp(), derived.maxFp(), gold, 0);
    }

    // ---- derived ----
    public PlayerStats derived()  { return derived; }
    public int maxHp()            { return derived.maxHp(); }
    public int maxFp()            { return derived.maxFp(); }
    public int level()            { return derived.level(); }

    // ---- stored ----
    public Attributes attributes() { return attributes; }
    public int hp()                { return hp; }
    public int fp()                { return fp; }
    public int gold()              { return gold; }
    public int shards()            { return shards; }
    public boolean isAlive()       { return hp > 0; }

    /** Heals up to max HP; returns the amount actually healed. O(1). */
    public int heal(int amount) {
        int before = hp;
        hp = clamp(hp + Math.max(0, amount), 0, maxHp());
        return hp - before;
    }

    public void setHp(int value) {
        hp = clamp(value, 0, maxHp());
    }

    /** Loses HP, never below 0; returns the amount actually lost. O(1). */
    public int damage(int amount) {
        int before = hp;
        hp = clamp(hp - Math.max(0, amount), 0, maxHp());
        return before - hp;
    }

    /** Spends FP if enough is available. O(1). */
    public boolean spendFp(int amount) {
        if (fp < amount) {
            return false;
        }
        fp -= amount;
        return true;
    }

    /** Restores FP up to the maximum; returns the amount restored. O(1). */
    public int restoreFp(int amount) {
        int before = fp;
        fp = clamp(fp + Math.max(0, amount), 0, maxFp());
        return fp - before;
    }

    /** HP and FP to their maximums (the interim respawn, P-55). O(1). */
    public void restoreAll() {
        hp = maxHp();
        fp = maxFp();
    }

    /** Adds (or with a negative amount removes) gold, never below 0. Returns the real change. O(1). */
    public int addGold(int amount) {
        int before = gold;
        gold = Math.max(0, gold + amount);
        return gold - before;
    }

    /** Adds Shards (V2_SPEC §4.3; spending arrives in V5). Returns the real change. O(1). */
    public int addShards(int amount) {
        int before = shards;
        shards = Math.max(0, shards + amount);
        return shards - before;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
