package com.shardbound.combat;

/**
 * Anything that takes turns in combat. The player and enemies store their
 * numbers differently (the player's hp lives in Stats), so the numbers are
 * abstract and each subclass supplies them (polymorphism).
 */
public abstract class Combatant {

    protected boolean defending;

    public abstract String name();
    public abstract int hp();
    public abstract int maxHp();
    public abstract int atk();
    public abstract int def();
    public abstract int spd();
    protected abstract void setHp(int hp);

    /** True for the player; used for turn-order tie-breaks (S1). */
    public abstract boolean isPlayer();

    public boolean isAlive() {
        return hp() > 0;
    }

    /** Lowers hp by {@code amount} (never below 0). O(1). */
    public void takeDamage(int amount) {
        setHp(Math.max(0, hp() - amount));
    }

    public boolean isDefending() {
        return defending;
    }

    public void setDefending(boolean defending) {
        this.defending = defending;
    }
}
