package com.shardbound.battle;

/**
 * One side of a battle. The player's HP lives in the game's stats (written
 * straight through); a creep's lives in its fighter, built fresh for each
 * battle from its template (D20).
 */
public abstract sealed class Fighter permits PlayerFighter, CreepFighter {

    private final StatusTracker statuses = new StatusTracker();
    private boolean guarding;

    public abstract String name();
    public abstract int level();
    public abstract Family family();
    public abstract String sprite();
    public abstract int hp();
    public abstract int maxHp();
    public abstract boolean isPlayer();

    /** Defence before statuses. */
    protected abstract int baseDefence();

    /** Speed before statuses. */
    protected abstract int baseSpeed();

    /** Critical chance, %. */
    public abstract int critPct();

    /** The raw power of a move by this fighter (attack rating, spell power or attack) times its power. */
    public abstract double raw(Skill move);

    /** Status chance bonus from Arcane, points. */
    public abstract int statusBonusPct();

    /** Status resistance, points. */
    public abstract int statusResistPct();

    /** Loses HP (never below 0); returns the HP actually lost. */
    public abstract int takeDamage(int amount);

    public boolean isAlive() {
        return hp() > 0;
    }

    public StatusTracker statuses() {
        return statuses;
    }

    public boolean guarding() {
        return guarding;
    }

    public void setGuarding(boolean guarding) {
        this.guarding = guarding;
    }

    /** Defence after statuses (Frost −30 %). */
    public double defence(BattleContent content) {
        return baseDefence() * statuses.defenceFactor(content.statuses());
    }

    /** Speed after statuses (Frost −3). */
    public int speed(BattleContent content) {
        return baseSpeed() + statuses.speedDelta(content.statuses());
    }
}
