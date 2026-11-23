package com.shardbound.progression;

import java.util.EnumMap;
import java.util.Map;

/**
 * Buying levels at a Lantern (V2_SPEC §4.3, P-61). Each point raised on an
 * attribute is one level; level L → L+1 costs {@code round(costBase · L^costExponent)},
 * and several levels bought at once cost the sum of their steps.
 */
public final class LevelUp {

    private final ProgressionRules rules;

    public LevelUp(ProgressionRules rules) {
        this.rules = rules;
    }

    /** What going from {@code level} to {@code level + 1} costs. */
    public int cost(int level) {
        return (int) Math.round(rules.levels().costBase() * Math.pow(level, rules.levels().costExponent()));
    }

    /**
     * A purchase: the attributes after {@code raises}, the levels bought, their total cost and
     * whether {@code shards} cover it.
     *
     * @throws IllegalArgumentException for a negative raise, or one past the attribute or level cap
     */
    public Plan plan(Attributes current, Map<Attribute, Integer> raises, int shards) {
        Map<Attribute, Integer> clean = new EnumMap<>(Attribute.class);
        raises.forEach((attribute, by) -> {
            if (by < 0) {
                throw new IllegalArgumentException("Attributes can only be raised");
            }
            if (by > 0) {
                clean.put(attribute, by);
            }
        });
        Attributes after = current.raised(clean);
        for (Attribute attribute : Attribute.values()) {
            if (after.get(attribute) > rules.levels().attributeMax()) {
                throw new IllegalArgumentException(attribute + " cannot go past " + rules.levels().attributeMax());
            }
        }
        if (after.level() > rules.levels().maxLevel()) {
            throw new IllegalArgumentException("The highest level is " + rules.levels().maxLevel());
        }
        int cost = 0;
        for (int level = current.level(); level < after.level(); level++) {
            cost += cost(level);
        }
        return new Plan(current, after, after.level() - current.level(), cost, cost <= shards);
    }

    /** A level-up purchase: before and after, how many levels, their cost and whether it is affordable. */
    public record Plan(Attributes before, Attributes after, int levels, int cost, boolean affordable) { }
}
