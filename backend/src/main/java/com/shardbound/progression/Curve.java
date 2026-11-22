package com.shardbound.progression;

import java.util.Map;
import java.util.TreeMap;

/**
 * A curve of points (attribute value → result) from balance.json, linearly
 * interpolated between neighbours and flat beyond the ends (V2_SPEC §4.3).
 */
public final class Curve {

    private final TreeMap<Integer, Double> points;

    public Curve(Map<Integer, Double> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A curve needs at least two points");
        }
        this.points = new TreeMap<>(points);
    }

    /** The value at {@code x}: floor and ceiling points found in O(log n), then interpolated. */
    public double at(int x) {
        Map.Entry<Integer, Double> low = points.floorEntry(x);
        Map.Entry<Integer, Double> high = points.ceilingEntry(x);
        if (low == null) {
            return high.getValue();
        }
        if (high == null || low.getKey().equals(high.getKey())) {
            return low.getValue();
        }
        double t = (double) (x - low.getKey()) / (high.getKey() - low.getKey());
        return low.getValue() + t * (high.getValue() - low.getValue());
    }
}
