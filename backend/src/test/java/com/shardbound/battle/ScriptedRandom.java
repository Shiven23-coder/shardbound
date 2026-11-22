package com.shardbound.battle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * A Random whose next values are scripted, so a test can pin a damage roll,
 * a crit or a status proc. Unscripted calls fall back to "no luck": a
 * middle roll (0.5) and the highest int (no crit, no proc).
 */
public final class ScriptedRandom extends Random {

    private final Deque<Double> doubles = new ArrayDeque<>();
    private final Deque<Integer> ints = new ArrayDeque<>();

    public ScriptedRandom rolls(double... values) {
        for (double v : values) {
            doubles.add(v);
        }
        return this;
    }

    public ScriptedRandom picks(int... values) {
        for (int v : values) {
            ints.add(v);
        }
        return this;
    }

    @Override
    public double nextDouble() {
        return doubles.isEmpty() ? 0.5 : doubles.poll();
    }

    @Override
    public int nextInt(int bound) {
        return ints.isEmpty() ? bound - 1 : Math.min(bound - 1, ints.poll());
    }
}
