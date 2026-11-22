package com.shardbound.battle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A fighter's statuses (V2_SPEC §4.5): turns left per status in an
 * {@code EnumMap}, plus turns of immunity after a status that grants it (Stun).
 * Statuses tick at the start of their owner's turn.
 */
public final class StatusTracker {

    private final EnumMap<Status, Integer> turnsLeft = new EnumMap<>(Status.class);
    private final EnumMap<Status, Integer> immunity = new EnumMap<>(Status.class);

    /** What a tick did: damage per status (in order) and whether the owner loses this turn. */
    public record Tick(List<Map.Entry<Status, Integer>> damage, boolean skipTurn) { }

    public boolean has(Status status) {
        return turnsLeft.containsKey(status);
    }

    /** True if the status could take hold now (not immune by family or by recent Stun). */
    public boolean canTake(StatusRule rule, Family family) {
        return rule.affects(family) && !immunity.containsKey(rule.status());
    }

    /** Puts a status on (or renews its turns). The caller checked {@link #canTake}. */
    public void apply(StatusRule rule) {
        turnsLeft.put(rule.status(), rule.turns());
    }

    /** A hit of {@code element} ends the statuses it removes (Frost ends Burn, Fire ends Frost). */
    public List<Status> removeBy(Element element, Map<Status, StatusRule> rules) {
        List<Status> removed = new ArrayList<>();
        for (Status status : List.copyOf(turnsLeft.keySet())) {
            if (rules.get(status).removedBy() == element) {
                turnsLeft.remove(status);
                removed.add(status);
            }
        }
        return removed;
    }

    /**
     * The start of the owner's turn: each damaging status deals its share of
     * {@code maxHp} (at least 1, never reduced by defence), a skipping status
     * costs the turn, every status loses a turn, and spent ones end (granting
     * their immunity). Immunity counts down too. O(s) for s statuses.
     */
    public Tick tick(int maxHp, Map<Status, StatusRule> rules) {
        immunity.replaceAll((status, turns) -> turns - 1);
        immunity.values().removeIf(turns -> turns <= 0);
        List<Map.Entry<Status, Integer>> damage = new ArrayList<>();
        boolean skip = false;
        for (Status status : List.copyOf(turnsLeft.keySet())) {
            StatusRule rule = rules.get(status);
            if (rule.tickPct() > 0) {
                damage.add(Map.entry(status, Math.max(1, (int) Math.round(maxHp * rule.tickPct() / 100.0))));
            }
            skip |= rule.skipsTurn();
            int left = turnsLeft.get(status) - 1;
            if (left <= 0) {
                turnsLeft.remove(status);
                if (rule.immuneTurnsAfter() > 0) {
                    immunity.put(status, rule.immuneTurnsAfter());
                }
            } else {
                turnsLeft.put(status, left);
            }
        }
        return new Tick(damage, skip);
    }

    /** Defence multiplier from statuses (Frost: −30 %). */
    public double defenceFactor(Map<Status, StatusRule> rules) {
        double factor = 1;
        for (Status status : turnsLeft.keySet()) {
            factor *= 1 + rules.get(status).defencePct() / 100.0;
        }
        return factor;
    }

    /** Speed change from statuses (Frost: −3). */
    public int speedDelta(Map<Status, StatusRule> rules) {
        int delta = 0;
        for (Status status : turnsLeft.keySet()) {
            delta += rules.get(status).speed();
        }
        return delta;
    }

    public Map<Status, Integer> active() {
        return new EnumMap<>(turnsLeft);
    }

    public Map<Status, Integer> immunities() {
        return new EnumMap<>(immunity);
    }

    /** Puts stored statuses back (resuming a battle). */
    public void restore(Map<Status, Integer> active, Map<Status, Integer> immune) {
        turnsLeft.clear();
        turnsLeft.putAll(active);
        immunity.clear();
        immunity.putAll(immune);
    }
}
