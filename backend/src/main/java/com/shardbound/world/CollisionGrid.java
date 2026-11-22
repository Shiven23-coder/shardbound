package com.shardbound.world;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.OptionalInt;

/**
 * Which tiles of an area block movement: a {@link BitSet} indexed
 * {@code y · width + x}. Immutable after construction.
 */
public final class CollisionGrid {

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final int width;
    private final int height;
    private final BitSet blocked;

    public CollisionGrid(int width, int height, BitSet blocked) {
        this.width = width;
        this.height = height;
        this.blocked = (BitSet) blocked.clone();
    }

    public int width()  { return width; }
    public int height() { return height; }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** A copy with every tile of the given rectangles blocked as well. */
    public CollisionGrid blocking(Collection<MapObject> rectangles) {
        BitSet more = (BitSet) blocked.clone();
        for (MapObject r : rectangles) {
            for (int y = Math.max(0, r.y()); y < Math.min(height, r.y() + r.height()); y++) {
                for (int x = Math.max(0, r.x()); x < Math.min(width, r.x() + r.width()); x++) {
                    more.set(y * width + x);
                }
            }
        }
        return new CollisionGrid(width, height, more);
    }

    public boolean walkable(int x, int y) {
        return inBounds(x, y) && !blocked.get(y * width + x);
    }

    /**
     * The length in tiles of the shortest 4-way walk between two walkable
     * tiles, or empty if there is none. Breadth-first search; time and space
     * O(width · height).
     */
    public OptionalInt pathLength(int fromX, int fromY, int toX, int toY) {
        if (!walkable(fromX, fromY) || !walkable(toX, toY)) {
            return OptionalInt.empty();
        }
        int target = toY * width + toX;
        int[] distance = new int[width * height];
        Arrays.fill(distance, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = fromY * width + fromX;
        distance[start] = 0;
        queue.add(start);
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            if (cell == target) {
                return OptionalInt.of(distance[cell]);
            }
            int x = cell % width;
            int y = cell / width;
            for (int[] step : STEPS) {
                int nx = x + step[0];
                int ny = y + step[1];
                if (walkable(nx, ny) && distance[ny * width + nx] < 0) {
                    distance[ny * width + nx] = distance[cell] + 1;
                    queue.add(ny * width + nx);
                }
            }
        }
        return OptionalInt.empty();
    }

    /** Every tile reachable from a start tile (the start included if walkable). Time O(width · height). */
    public BitSet reachableFrom(int fromX, int fromY) {
        BitSet seen = new BitSet(width * height);
        if (!walkable(fromX, fromY)) {
            return seen;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen.set(fromY * width + fromX);
        queue.add(fromY * width + fromX);
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int[] step : STEPS) {
                int nx = cell % width + step[0];
                int ny = cell / width + step[1];
                if (walkable(nx, ny) && !seen.get(ny * width + nx)) {
                    seen.set(ny * width + nx);
                    queue.add(ny * width + nx);
                }
            }
        }
        return seen;
    }
}
