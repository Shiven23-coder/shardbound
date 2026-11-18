package com.shardbound.rules;

import com.shardbound.engine.GameState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Conditions in disjunctive normal form (D4): an OR of AND-groups.
 * Conditions with the same {@code grp} number must ALL hold; the whole set
 * passes if ANY group passes. No conditions at all means "always true".
 *
 * <p>Stored in a {@code TreeMap<Integer, List<Condition>>} so groups are
 * tested and described in ascending grp order even when numbers have gaps.
 * Used for both choices and endings.
 */
public final class ConditionGroup {

    private static final ConditionGroup EMPTY = new ConditionGroup(new TreeMap<>());

    private final TreeMap<Integer, List<Condition>> groups;

    private ConditionGroup(TreeMap<Integer, List<Condition>> groups) {
        this.groups = groups;
    }

    public static ConditionGroup empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * True if there are no conditions, or some group has all its conditions
     * true. Stops at the first passing group / first failing condition.
     * Time O(c) for c conditions, space O(1).
     */
    public boolean test(GameState state) {
        if (groups.isEmpty()) {
            return true;
        }
        for (List<Condition> group : groups.values()) {
            if (allPass(group, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lock reason, e.g. "Requires affinity with Elder Maren ≥ 20, or quest "A Strange Light" completed".
     * Time O(c), space O(c).
     */
    public String describe() {
        if (groups.isEmpty()) {
            return "";
        }
        List<String> groupTexts = new ArrayList<>();
        for (List<Condition> group : groups.values()) {
            List<String> parts = new ArrayList<>();
            for (Condition condition : group) {
                parts.add(condition.requirement());
            }
            groupTexts.add(String.join(" and ", parts));
        }
        return "Requires " + String.join(", or ", groupTexts);
    }

    /** True if any condition is a REP or AFFINITY check (SILVER_TONGUE counter, D15). O(c). */
    public boolean containsRepOrAffinity() {
        for (Condition condition : all()) {
            if (condition instanceof RepCondition || condition instanceof AffinityCondition) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    public int groupCount() {
        return groups.size();
    }

    /** Every condition in grp order (read-only copy). O(c). */
    public List<Condition> all() {
        List<Condition> result = new ArrayList<>();
        for (List<Condition> group : groups.values()) {
            result.addAll(group);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean allPass(List<Condition> group, GameState state) {
        for (Condition condition : group) {
            if (!condition.test(state)) {
                return false;
            }
        }
        return true;
    }

    /** Collects conditions by grp, then freezes them into an immutable ConditionGroup. */
    public static final class Builder {
        private final TreeMap<Integer, List<Condition>> groups = new TreeMap<>();

        /** Adds a condition to group {@code grp}. O(log g) for g groups. */
        public Builder add(int grp, Condition condition) {
            groups.computeIfAbsent(grp, g -> new ArrayList<>()).add(condition);
            return this;
        }

        public ConditionGroup build() {
            if (groups.isEmpty()) {
                return EMPTY;
            }
            TreeMap<Integer, List<Condition>> frozen = new TreeMap<>();
            for (Map.Entry<Integer, List<Condition>> entry : groups.entrySet()) {
                frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return new ConditionGroup(frozen);
        }
    }
}
