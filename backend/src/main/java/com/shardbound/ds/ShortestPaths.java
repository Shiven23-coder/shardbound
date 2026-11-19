package com.shardbound.ds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The result of {@link Graph#dijkstra}: the shortest distance from the source to
 * every REACHABLE vertex, and the parent of each vertex on one shortest path.
 * Unreachable vertices are simply absent from both maps.
 *
 * @param source    the start vertex
 * @param distances vertex → total weight of a shortest path (source → 0)
 * @param parents   vertex → previous vertex on that path (the source has none)
 */
public record ShortestPaths<V>(V source, Map<V, Long> distances, Map<V, V> parents) {

    public ShortestPaths {
        distances = Collections.unmodifiableMap(distances);
        parents = Collections.unmodifiableMap(parents);
    }

    /** True if {@code target} can be reached from the source. O(1). */
    public boolean reachable(V target) {
        return distances.containsKey(target);
    }

    /** Shortest distance to {@code target}, or -1 if unreachable. O(1). */
    public long distanceTo(V target) {
        Long distance = distances.get(target);
        return distance == null ? -1 : distance;
    }

    /**
     * The vertices of a shortest path source → target (both included), or an
     * empty list if unreachable. Walks the parent links back from the target
     * and reverses them. Time O(path length), space O(path length).
     */
    public List<V> pathTo(V target) {
        List<V> path = new ArrayList<>();
        if (!reachable(target)) {
            return path;
        }
        V step = target;
        while (step != null) {
            path.add(step);
            step = parents.get(step);
        }
        Collections.reverse(path);
        return path;
    }
}
