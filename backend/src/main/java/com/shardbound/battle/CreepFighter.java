package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * An enemy in battle, built from its template at a level (V2_SPEC §4.2):
 * {@code hp = round(baseHp · (1 + 0.12(lvl−1)))}, atk and def likewise with
 * their own growth, speed unscaled. Holds its own HP, its AI memory and its
 * announced telegraphed moves (an {@code ArrayDeque}, P-54). A boss (P-67) is a
 * creep fighter with its {@link BossTemplate}: its phase follows from its HP.
 */
public final class CreepFighter extends Fighter {

    private final CreepTemplate template;
    private final @Nullable BossTemplate boss;
    private final int level;
    private final int maxHp;
    private final int atk;
    private final int def;
    private final int critPct;
    private final Deque<Skill> telegraphs = new ArrayDeque<>();
    private int hp;
    private boolean guardedOnce;

    public CreepFighter(CreepTemplate template, int level, BattleRules rules) {
        this(template, null, level, rules);
    }

    /** A boss at its own level. */
    public CreepFighter(BossTemplate boss, BattleRules rules) {
        this(boss.asCreep(), boss, boss.level(), rules);
    }

    private CreepFighter(CreepTemplate template, @Nullable BossTemplate boss, int level, BattleRules rules) {
        this.template = template;
        this.boss = boss;
        this.level = level;
        this.maxHp = BattleRules.scaled(template.baseHp(), rules.hpPerLevel(), level);
        this.atk = BattleRules.scaled(template.baseAtk(), rules.atkPerLevel(), level);
        this.def = BattleRules.scaled(template.baseDef(), rules.defPerLevel(), level);
        this.critPct = rules.enemyCritPct();
        this.hp = maxHp;
    }

    public CreepTemplate template()   { return template; }
    public @Nullable BossTemplate boss() { return boss; }

    /** A boss's phase from its HP: 2 once HP is at or below the second phase's share (V2_SPEC §4.7); a creep is always 1. */
    public int phase() {
        return boss != null && hp * 100L <= (long) maxHp * boss.phases().get(1).atPct() ? 2 : 1;
    }

    /** The boss's current phase. */
    public BossTemplate.Phase bossPhase() {
        if (boss == null) {
            throw new IllegalStateException(name() + " is not a boss");
        }
        return boss.phases().get(phase() - 1);
    }
    public int atk()                  { return atk; }

    @Override public String name()        { return template.name(); }
    @Override public int level()          { return level; }
    @Override public Family family()      { return template.family(); }
    @Override public String sprite()      { return template.sprite(); }
    @Override public int hp()             { return hp; }
    @Override public int maxHp()          { return maxHp; }
    @Override public boolean isPlayer()   { return false; }
    @Override protected int baseDefence() { return def; }
    @Override protected int baseSpeed()   { return template.baseSpd() + (boss != null && phase() == 2 ? bossPhase().spdDelta() : 0); }
    @Override public int critPct()        { return critPct; }
    @Override public int statusBonusPct() { return 0; }
    @Override public int statusResistPct() { return 0; }

    /** A creep's move hits with its attack times the move's power (P-54). */
    @Override
    public double raw(Skill move) {
        return atk * move.power();
    }

    @Override
    public int takeDamage(int amount) {
        int before = hp;
        hp = Math.max(0, hp - Math.max(0, amount));
        return before - hp;
    }

    /** Whether a DEFENSIVE creep has used its one guard (AI memory). */
    public boolean guardedOnce()              { return guardedOnce; }
    public void markGuarded()                 { guardedOnce = true; }

    /** Announces a telegraphed move: it lands on this creep's next turn. */
    public void announce(Skill move)          { telegraphs.add(move); }

    /** The move announced last turn, if any, taken off the queue. */
    public Optional<Skill> nextTelegraph()    { return Optional.ofNullable(telegraphs.poll()); }

    public List<Skill> pendingTelegraphs()    { return List.copyOf(telegraphs); }

    /** Puts stored state back (resuming a battle). */
    public void restore(int storedHp, boolean storedGuardedOnce, List<Skill> storedTelegraphs) {
        hp = Math.max(0, Math.min(maxHp, storedHp));
        guardedOnce = storedGuardedOnce;
        telegraphs.clear();
        telegraphs.addAll(storedTelegraphs);
    }
}
