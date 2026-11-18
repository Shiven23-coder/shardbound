package com.shardbound.ds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 * are viva algorithms (CLAUDE.md rule 8): step-by-step comments here, a dry run
 * and viva questions in docs/STUDY_GUIDE.md, tests in {@code GraphTest}.
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
    // Viva algorithms (CLAUDE.md rule 8; explained in docs/STUDY_GUIDE.md)
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
     * <p>Complexity: time O(V + E) — each vertex is queued/pushed and expanded at most once, each edge is examined once; space O(V) for the visited set and the queue/stack.
     * Used by StoryValidator checks 1 and 4 (reachability).
     */
    public List<V> bfs(V start) {
        List<V> order = new ArrayList<>();
        // Step 0: an unknown start vertex reaches nothing.
        if (!adjacency.containsKey(start)) {
            return order;
        }
        // Step 1: "visited" marks a vertex the moment it is DISCOVERED (added to the queue),
        // so no vertex can ever be queued twice.
        Set<V> visited = new HashSet<>();
        ArrayDeque<V> queue = new ArrayDeque<>();      // FIFO: add at the tail, poll from the head
        visited.add(start);
        queue.add(start);
        // Step 2: take the oldest discovered vertex, record it, discover its unseen neighbours.
        // Because the queue is FIFO, every vertex at distance d is recorded before any at d + 1.
        while (!queue.isEmpty()) {
            V current = queue.poll();
            order.add(current);
            for (V next : neighbors(current)) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return order;
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
     * <p>Complexity: time O(V + E) — each vertex is expanded at most once and each
     * edge causes at most one push; space O(V + E) worst case, because a vertex can
     * sit on the stack several times (pushed by different neighbours) before its
     * first pop. Used by StoryValidator check 2 (dead ends).
     */
    public List<V> dfs(V start) {
        List<V> order = new ArrayList<>();
        // Step 0: an unknown start vertex reaches nothing.
        if (!adjacency.containsKey(start)) {
            return order;
        }
        // Step 1: an explicit stack replaces recursion, so a 100 000-vertex chain
        // cannot overflow the Java call stack.
        Set<V> visited = new HashSet<>();
        ArrayDeque<V> stack = new ArrayDeque<>();      // LIFO: push and pop at the head
        stack.push(start);
        while (!stack.isEmpty()) {
            // Step 2: a vertex counts as visited when it is POPPED. It may have been pushed
            // more than once (by different neighbours); later copies are simply skipped.
            V current = stack.pop();
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);
            order.add(current);
            // Step 3: push neighbours in REVERSE order, so the first neighbour ends up on top
            // and is explored first: the same order a recursive DFS would use.
            List<V> next = neighbors(current);
            for (int i = next.size() - 1; i >= 0; i--) {
                if (!visited.contains(next.get(i))) {
                    stack.push(next.get(i));
                }
            }
        }
        return order;
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
     * <p>Complexity: time O(V + E) — each vertex is queued/pushed and expanded at most once, each edge is examined once; space O(V) for the visited set and the queue/stack.
     * Used by StoryValidator check 5 (quick route ≤ 12 steps).
     */
    public List<V> shortestPath(V from, V to) {
        // Step 0: both ends must exist.
        if (!adjacency.containsKey(from) || !adjacency.containsKey(to)) {
            return new ArrayList<>();
        }
        // Step 1: BFS from 'from'. parent.get(v) is the vertex v was discovered from;
        // containsKey(v) doubles as "already discovered". The start has no parent.
        Map<V, V> parent = new HashMap<>();
        ArrayDeque<V> queue = new ArrayDeque<>();
        parent.put(from, null);
        queue.add(from);
        // Step 2: stop as soon as 'to' has been discovered. BFS discovers every vertex
        // along a fewest-edges route, so the first discovery of 'to' is a shortest path.
        while (!queue.isEmpty() && !parent.containsKey(to)) {
            V current = queue.poll();
            for (V next : neighbors(current)) {
                if (!parent.containsKey(next)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        // Step 3: never discovered → unreachable.
        if (!parent.containsKey(to)) {
            return new ArrayList<>();
        }
        // Step 4: walk the parent links back from 'to' to 'from' (the path in reverse),
        // then reverse it so it reads from → to.
        List<V> path = new ArrayList<>();
        V step = to;
        while (step != null) {
            path.add(step);
            step = parent.get(step);
        }
        Collections.reverse(path);
        return path;
    }
}
