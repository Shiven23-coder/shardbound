package com.shardbound.battle;

import java.util.EnumMap;
import java.util.Map;

/**
 * The family × element chart from content/elements.json: how hard each
 * element hits each family. Immutable after loading.
 */
public final class ElementChart {

    private final Map<Family, EnumMap<Element, Affinity>> chart;
    private final EnumMap<Affinity, Double> multipliers;

    public ElementChart(Map<Family, EnumMap<Element, Affinity>> chart, Map<Affinity, Double> multipliers) {
        this.chart = new EnumMap<>(Family.class);
        chart.forEach((family, row) -> this.chart.put(family, new EnumMap<>(row)));
        this.multipliers = new EnumMap<>(multipliers);
    }

    public Affinity affinity(Family family, Element element) {
        return chart.get(family).get(element);
    }

    /** The damage multiplier of {@code element} against {@code family}: W 1.5, N 1, R 0.5, I 0. O(1). */
    public double multiplier(Family family, Element element) {
        return multipliers.get(affinity(family, element));
    }
}
