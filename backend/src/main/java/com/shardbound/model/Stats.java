package com.shardbound.model;

/**
 * The player's numeric state.
 *
 * <p>Only five numbers are stored (and saved): hp, energy, level, xp, gold.
 * Everything else is DERIVED from {@code level} (D6, D28), so a save can never
 * hold an atk or max hp that disagrees with its level.
 *
 * <p>{@code xp} is progress inside the current level and resets to the
 * leftover on level-up (D29), so level is not derivable from xp.
 */
public class Stats {

    public static final int BASE_HP = 40;
    public static final int HP_PER_LEVEL = 5;
    public static final int BASE_ATK = 6;
    public static final int ATK_PER_LEVEL = 1;
    public static final int BASE_DEF = 2;
    public static final int BASE_SPD = 5;
    public static final int MAX_ENERGY = 3;
    public static final int XP_PER_LEVEL_STEP = 100;

    private int hp;
    private int energy;
    private int level;
    private int xp;
    private int gold;

    public Stats(int hp, int energy, int level, int xp, int gold) {
        this.level = Math.max(1, level);
        this.hp = clamp(hp, 0, maxHp());
        this.energy = clamp(energy, 0, maxEnergy());
        this.xp = Math.max(0, xp);
        this.gold = Math.max(0, gold);
    }

    /** Starting stats from spec §6.1. O(1). */
    public static Stats starting() {
        return new Stats(BASE_HP, MAX_ENERGY, 1, 0, 15);
    }

    /**
     * Max hp for a level. The SAME expression appears in the save_slot CHECK
     * constraint ({@code 40 + 5 * (level - 1)}); MaxHpFormulaTest compares them. O(1).
     */
    public static int maxHpForLevel(int level) {
        return BASE_HP + HP_PER_LEVEL * (level - 1);
    }

    // ---- derived stats (O(1) each) ----
    public int maxHp()     { return maxHpForLevel(level); }
    public int atk()       { return BASE_ATK + ATK_PER_LEVEL * (level - 1); }
    public int def()       { return BASE_DEF; }
    public int spd()       { return BASE_SPD; }
    public int maxEnergy() { return MAX_ENERGY; }
    public int xpToNext()  { return XP_PER_LEVEL_STEP * level; }

    // ---- stored stats ----
    public int hp()     { return hp; }
    public int energy() { return energy; }
    public int level()  { return level; }
    public int xp()     { return xp; }
    public int gold()   { return gold; }

    /**
     * Adds xp and levels up while xp reaches the threshold, keeping the
     * leftover each time (D29). Returns how many levels were gained.
     * Time O(L) for L levels gained, space O(1).
     */
    public int addXp(int amount) {
        xp += Math.max(0, amount);
        int levelsGained = 0;
        while (xp >= xpToNext()) {
            xp -= xpToNext();
            level++;
            levelsGained++;
            hp = Math.min(hp + HP_PER_LEVEL, maxHp());   // the new max hp is partly "filled"
        }
        return levelsGained;
    }

    /** Heals up to max hp; returns the amount actually healed. O(1). */
    public int heal(int amount) {
        int before = hp;
        hp = clamp(hp + Math.max(0, amount), 0, maxHp());
        return hp - before;
    }

    /** Sets hp, clamped to 0..maxHp. O(1). */
    public void setHp(int value) {
        hp = clamp(value, 0, maxHp());
    }

    /** Adds (or with a negative amount, removes) gold; never goes below 0. Returns the real change. O(1). */
    public int addGold(int amount) {
        int before = gold;
        gold = Math.max(0, gold + amount);
        return gold - before;
    }

    /** Spends energy if enough is available. O(1). */
    public boolean spendEnergy(int amount) {
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    }

    /** Restores energy up to the maximum; returns the amount restored. O(1). */
    public int restoreEnergy(int amount) {
        int before = energy;
        energy = clamp(energy + Math.max(0, amount), 0, maxEnergy());
        return energy - before;
    }

    /** Fills energy to the maximum (start of every combat, S2). O(1). */
    public void refillEnergy() {
        energy = maxEnergy();
    }

    /** Independent copy (used by GameState.deepCopy). O(1). */
    public Stats copy() {
        return new Stats(hp, energy, level, xp, gold);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
