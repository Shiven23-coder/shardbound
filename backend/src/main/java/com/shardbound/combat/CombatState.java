package com.shardbound.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One fight in progress. Private fields (D30): CombatEngine (same package)
 * changes it; everyone else reads unmodifiable views.
 *
 * <p>The log is a bounded {@code ArrayDeque<String>} holding the last
 * {@link #LOG_SIZE} lines: add at the tail, drop from the head, both O(1).
 */
public class CombatState {

    public static final int LOG_SIZE = 8;

    private final String encounterId;
    private final String encounterName;
    private final PlayerCombatant player;
    private final List<Enemy> enemies;
    private final ArrayDeque<String> log = new ArrayDeque<>();
    private final boolean canFlee;
    private int round;
    private List<Hit> lastHits = new ArrayList<>();

    CombatState(String encounterId, String encounterName, PlayerCombatant player, List<Enemy> enemies,
                boolean canFlee) {
        this.encounterId = encounterId;
        this.encounterName = encounterName;
        this.player = player;
        this.enemies = new ArrayList<>(enemies);
        this.canFlee = canFlee;
    }

    // ---- public read-only API ----
    public String encounterId()           { return encounterId; }
    public String encounterName()         { return encounterName; }
    public PlayerCombatant player()       { return player; }
    public boolean canFlee()              { return canFlee; }
    public int round()                    { return round; }
    public List<Enemy> enemiesView()      { return Collections.unmodifiableList(enemies); }
    public List<String> logView()         { return List.copyOf(log); }
    public List<Hit> lastHitsView()       { return Collections.unmodifiableList(lastHits); }

    /** True if any enemy in this fight is a boss (boss losses offer a reload, S3). O(n). */
    public boolean hasBoss() {
        for (Enemy enemy : enemies) {
            if (enemy.isBoss()) {
                return true;
            }
        }
        return false;
    }

    /** True when every enemy is down. O(n). */
    public boolean allEnemiesDefeated() {
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The enemy at {@code index} if it is alive, otherwise the first living
     * enemy (so a stale target never wastes the player's turn). O(n).
     */
    public Enemy targetOrFirstAlive(int index) {
        if (index >= 0 && index < enemies.size() && enemies.get(index).isAlive()) {
            return enemies.get(index);
        }
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) {
                return enemy;
            }
        }
        return null;
    }

    /** Average speed of living enemies (flee chance). O(n). */
    public double averageEnemySpeed() {
        int total = 0;
        int count = 0;
        for (Enemy enemy : enemies) {
            if (enemy.isAlive()) {
                total += enemy.spd();
                count++;
            }
        }
        return count == 0 ? 0 : (double) total / count;
    }

    // ---- package-private: only CombatEngine changes the fight ----

    List<Enemy> enemies() {
        return enemies;
    }

    /** Appends a line, dropping the oldest when more than LOG_SIZE. O(1). */
    void addLog(String line) {
        log.addLast(line);
        if (log.size() > LOG_SIZE) {
            log.removeFirst();
        }
    }

    void startRound() {
        round++;
        lastHits = new ArrayList<>();
    }

    void recordHit(Hit hit) {
        lastHits.add(hit);
    }
}
