package com.shardbound.ds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A directed graph stored as an adjacency list.
 *
 * <p>{@code V} is the vertex type (story node ids are Strings) and {@code E}
 * is the value carried by each edge (a label such as "choice 12" or "win").
 * An adjacency list suits the story graph because it is sparse: each node has
 * only 1–4 exits, so storing edges per vertex costs O(V + E) memory instead of
 * the O(V²) of an adjacency matrix.
 *
 * <p>The traversal methods {@link #bfs}, {@link #dfs} and {@link #shortestPath}
 * are <b>student-implemented</b> (CLAUDE.md rule 8). Their tests are in
 * {@code GraphTest} and fail until they are written.
 *
 * @param <V> vertex type; must implement equals/hashCode correctly
 * @param <E> edge value type
 */
public class Graph<V, E> {

    // Insertion-ordered so traversals and printouts are deterministic.
    private final Map<V, List<Edge<V, E>>> adjacency = new HashMap<>();
    private final Set<V> vertexOrder = new LinkedHashSet<>();
    private int edgeCount = 0;

    /** Adds a vertex if it is not already present. Time O(1) average, space O(1). */
    public void addVertex(V v) {
        if (!adjacency.containsKey(v)) {
            adjacency.put(v, new ArrayList<>());
            vertexOrder.add(v);
        }
    }

    /**
     * Adds a directed edge from → to, creating either vertex if needed.
     * Parallel edges are allowed (two choices can lead to the same node).
     * Time O(1) average, space O(1).
     */
    public void addEdge(V from, V to, E value) {
        addVertex(from);
        addVertex(to);
        adjacency.get(from).add(new Edge<>(to, value));
        edgeCount++;
    }

    /** True if the vertex exists. O(1) average. */
    public boolean containsVertex(V v) {
        return adjacency.containsKey(v);
    }

    /**
     * Outgoing edges of {@code v} in insertion order (empty list for an unknown
     * vertex). Returns an unmodifiable view. Time O(1), space O(1).
     */
    public List<Edge<V, E>> edgesFrom(V v) {
        List<Edge<V, E>> edges = adjacency.get(v);
        if (edges == null) {
            return List.of();
        }
        return Collections.unmodifiableList(edges);
    }

    /**
     * Distinct target vertices reachable in one step from {@code v}, in the
     * order their first edge was added. Time O(k) for k outgoing edges, space O(k).
     */
    public List<V> neighbors(V v) {
        Set<V> targets = new LinkedHashSet<>();
        for (Edge<V, E> edge : edgesFrom(v)) {
            targets.add(edge.to());
        }
        return new ArrayList<>(targets);
    }

    /** All vertices in insertion order (unmodifiable). O(1). */
    public Set<V> vertices() {
        return Collections.unmodifiableSet(vertexOrder);
    }

    public int vertexCount() {
        return adjacency.size();
    }

    public int edgeCount() {
        return edgeCount;
    }

    // =====================================================================
    // Student-implemented traversals (CLAUDE.md rule 8)
    // =====================================================================

    /**
     * Breadth-first search from {@code start}.
     *
     * <p>Returns every vertex reachable from {@code start} (including
     * {@code start} itself) in the order BFS visits them: level by level,
     * and within a level in the order of {@link #neighbors}. Each vertex
     * appears once. Returns an empty list if {@code start} is not in the graph.
     *
     * <p>Use an {@code ArrayDeque} as the FIFO queue and a {@code HashSet}
     * for "visited".
     *
     * <p>Expected complexity: time O(V + E), space O(V).
     * Used by StoryValidator checks 1 and 4 (reachability).
     */
    public List<V> bfs(V start) {
        // TODO(student): implement BFS (see GraphTest for the expected behaviour)
        throw new UnsupportedOperationException("TODO(student): Graph.bfs");
    }

    /**
     * Iterative depth-first search from {@code start} (no recursion).
     *
     * <p>Returns every vertex reachable from {@code start} (including
     * {@code start}) in DFS visit order, each once. Use an {@code ArrayDeque}
     * as a LIFO stack. To visit neighbours in their natural order, push them
     * in REVERSE order of {@link #neighbors}. A vertex counts as visited when
     * it is popped (skip it if already visited). Returns an empty list if
     * {@code start} is not in the graph.
     *
     * <p>Expected complexity: time O(V + E), space O(V).
     * Used by StoryValidator check 2 (dead ends).
     */
    public List<V> dfs(V start) {
        // TODO(student): implement iterative DFS (see GraphTest)
        throw new UnsupportedOperationException("TODO(student): Graph.dfs");
    }

    /**
     * Shortest path (fewest edges) from {@code from} to {@code to}.
     *
     * <p>Run BFS from {@code from} while recording, for each newly discovered
     * vertex, the vertex it was discovered from (a "parent map"). Stop when
     * {@code to} is discovered, then walk the parent map backwards from
     * {@code to} and reverse it.
     *
     * <p>Returns the vertices of the path including both ends
     * ({@code [from]} when {@code from.equals(to)}), or an empty list if
     * {@code to} is unreachable or either vertex is missing. The path length in
     * edges is {@code size() - 1}.
     *
     * <p>Expected complexity: time O(V + E), space O(V).
     * Used by StoryValidator check 5 (quick route ≤ 12 steps).
     */
    public List<V> shortestPath(V from, V to) {
        // TODO(student): implement BFS with a parent map (see GraphTest)
        throw new UnsupportedOperationException("TODO(student): Graph.shortestPath");
    }
}
