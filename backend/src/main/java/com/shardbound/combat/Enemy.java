package com.shardbound.combat;

import com.shardbound.combat.ai.EnemyAI;

/**
 * One enemy in ONE fight. Created fresh from an immutable EnemyTemplate for
 * every fight (D20), so no session can damage another session's enemy.
 */
public class Enemy extends Combatant {

    public static final double DEMO_SCALE = 0.6;

    private final int index;
    private final String name;
    private int hp;
    private final int maxHp;
    private final int atk;
    private final int def;
    private final int spd;
    private final int xpReward;
    private final int goldReward;
    private final EnemyAI ai;
    private final boolean boss;
    private int turnsTaken;

    private Enemy(int index, String name, int maxHp, int atk, int def, int spd,
                  int xpReward, int goldReward, EnemyAI ai, boolean boss) {
        this.index = index;
        this.name = name;
        this.hp = maxHp;
        this.maxHp = maxHp;
        this.atk = atk;
        this.def = def;
        this.spd = spd;
        this.xpReward = xpReward;
        this.goldReward = goldReward;
        this.ai = ai;
        this.boss = boss;
    }

    /**
     * Builds a fresh enemy. Demo Mode multiplies hp and atk by 0.6 (spec §6.6).
     * Each enemy gets its own AI object because some AIs remember things. O(1).
     */
    public static Enemy from(EnemyTemplate t, int index, String displayName, boolean demoMode) {
        int hp = demoMode ? scale(t.hp()) : t.hp();
        int atk = demoMode ? scale(t.atk()) : t.atk();
        return new Enemy(index, displayName, hp, atk, t.def(), t.spd(), t.xpReward(), t.goldReward(),
                EnemyAI.forType(t.ai()), "BOSS".equals(t.ai()));
    }

    private static int scale(int value) {
        return Math.max(1, (int) Math.round(value * DEMO_SCALE));
    }

    public int index()        { return index; }
    public int xpReward()     { return xpReward; }
    public int goldReward()   { return goldReward; }
    public EnemyAI ai()       { return ai; }
    public boolean isBoss()   { return boss; }
    public int turnsTaken()   { return turnsTaken; }
    void countTurn()          { turnsTaken++; }

    /** Puts back the state a stored fight recorded for this enemy. */
    void restore(CombatSnapshot.EnemyEntry entry) {
        this.hp = Math.max(0, Math.min(maxHp, entry.hp()));
        this.defending = entry.defending();
        this.turnsTaken = entry.turnsTaken();
        ai.restoreMemory(entry.aiMemory());
    }

    @Override public String name()  { return name; }
    @Override public int hp()       { return hp; }
    @Override public int maxHp()    { return maxHp; }
    @Override public int atk()      { return atk; }
    @Override public int def()      { return def; }
    @Override public int spd()      { return spd; }
    @Override protected void setHp(int hp) { this.hp = hp; }
    @Override public boolean isPlayer() { return false; }
}
