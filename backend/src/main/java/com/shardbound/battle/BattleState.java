package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One battle in progress (or just finished): the two fighters, the round,
 * the lines and hits of the last round, and where the battle came from: a
 * creep on the map ({@code creepRef}, "area/object") or a dialogue encounter.
 */
public final class BattleState {

    /** What a won battle gave the player. */
    public record Rewards(int shards, List<String> itemIds) {
        public Rewards {
            itemIds = List.copyOf(itemIds);
        }
    }

    private final PlayerFighter player;
    private final CreepFighter enemy;
    private final @Nullable String creepRef;
    private final @Nullable String encounterId;
    private final boolean canFlee;
    private final List<String> text = new ArrayList<>();
    private final List<HitLine> hits = new ArrayList<>();
    private int round;
    private @Nullable Integer phaseLine;
    private BattleOutcome outcome = BattleOutcome.ONGOING;
    private @Nullable Rewards rewards;

    public BattleState(PlayerFighter player, CreepFighter enemy, @Nullable String creepRef,
                       @Nullable String encounterId, boolean canFlee) {
        this.player = player;
        this.enemy = enemy;
        this.creepRef = creepRef;
        this.encounterId = encounterId;
        this.canFlee = canFlee;
    }

    public PlayerFighter player()          { return player; }
    public CreepFighter enemy()            { return enemy; }
    public @Nullable String creepRef()     { return creepRef; }
    public @Nullable String encounterId()  { return encounterId; }
    public boolean canFlee()               { return canFlee; }
    public int round()                     { return round; }
    public List<String> text()             { return List.copyOf(text); }
    public List<HitLine> hits()            { return List.copyOf(hits); }
    public BattleOutcome outcome()         { return outcome; }
    public @Nullable Rewards rewards()     { return rewards; }

    /** The index in this round's text of the line where a boss turned to its second phase, else null (P-67). */
    public @Nullable Integer phaseLine()   { return phaseLine; }

    /** The announced move's name while one is pending, for the persistent warning (P-67), else null. */
    public @Nullable String telegraphMove() {
        return enemy.pendingTelegraphs().stream().findFirst().map(Skill::name).orElse(null);
    }

    /** The announced move's line while one is pending, else null. */
    public @Nullable String telegraph() {
        return enemy.pendingTelegraphs().stream().findFirst()
                .map(move -> move.telegraphLine().replace("{name}", enemy.name()))
                .orElse(null);
    }

    void startRound() {
        round++;
        text.clear();
        hits.clear();
        phaseLine = null;
    }

    /** The index the next line of the round's text will have. */
    int nextLine() {
        return text.size();
    }

    /** A line added after the battle has ended, by what follows it (where a death wakes the player, P-62). */
    public void close(String line) {
        text.add(line);
    }

    /** Marks the line just said as a boss's turn to its second phase. */
    void markPhaseLine() {
        phaseLine = text.size() - 1;
    }

    void say(String line) {
        text.add(line);
    }

    void record(HitLine hit) {
        hits.add(hit);
    }

    void end(BattleOutcome result, @Nullable Rewards won) {
        this.outcome = result;
        this.rewards = won;
    }

    /** Puts a stored round back (resuming a battle). */
    void restore(int storedRound, List<String> storedText, List<HitLine> storedHits) {
        round = storedRound;
        text.clear();
        text.addAll(storedText);
        hits.clear();
        hits.addAll(storedHits);
    }
}
