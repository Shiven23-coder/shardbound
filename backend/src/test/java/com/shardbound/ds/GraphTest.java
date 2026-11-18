package com.shardbound.ds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Graph. The structure tests pass now; the bfs / dfs /
 * shortestPath tests FAIL until the student implements those methods
 * (CLAUDE.md rule 8).
 *
 * <pre>
 *   A → B → D → F
 *   A → C → D
 *   C → E          (E is a dead end)
 *   G → A          (G is not reachable from A)
 *   F → B          (a cycle B → D → F → B)
 * </pre>
 */
class GraphTest {

    private Graph<String, String> g;

    @BeforeEach
    void setUp() {
        g = new Graph<>();
        g.addEdge("A", "B", "ab");
        g.addEdge("A", "C", "ac");
        g.addEdge("B", "D", "bd");
        g.addEdge("C", "D", "cd");
        g.addEdge("C", "E", "ce");
        g.addEdge("D", "F", "df");
        g.addEdge("F", "B", "fb");
        g.addEdge("G", "A", "ga");
    }

    // ---------------------------------------------------------------------
    // structure (already implemented)
    // ---------------------------------------------------------------------

    @Test
    void countsVerticesAndEdges() {
        assertEquals(7, g.vertexCount());
        assertEquals(8, g.edgeCount());
    }

    @Test
    void neighborsKeepInsertionOrder() {
        assertEquals(List.of("B", "C"), g.neighbors("A"));
        assertEquals(List.of(), g.neighbors("E"));
        assertEquals(List.of(), g.neighbors("missing"));
    }

    @Test
    void parallelEdgesAreKeptButNeighborsAreDistinct() {
        g.addEdge("A", "B", "second choice to B");
        assertEquals(3, g.edgesFrom("A").size());
        assertEquals(List.of("B", "C"), g.neighbors("A"));
    }

    // ---------------------------------------------------------------------
    // BFS (student)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("bfs visits level by level, each vertex once")
    void bfsVisitsLevelByLevel() {
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), g.bfs("A"));
    }

    @Test
    void bfsDoesNotReachVerticesWithNoPathFromStart() {
        assertTrue(!g.bfs("A").contains("G"));
        assertEquals(List.of("E"), g.bfs("E"));
    }

    @Test
    void bfsFromMissingVertexIsEmpty() {
        assertEquals(List.of(), g.bfs("Z"));
    }

    // ---------------------------------------------------------------------
    // DFS (student)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("dfs goes deep first, neighbours in natural order, cycles handled")
    void dfsGoesDeepFirst() {
        assertEquals(List.of("A", "B", "D", "F", "C", "E"), g.dfs("A"));
    }

    @Test
    void dfsFromMissingVertexIsEmpty() {
        assertEquals(List.of(), g.dfs("Z"));
    }

    @Test
    void dfsHandlesALongChainWithoutRecursion() {
        Graph<Integer, String> chain = new Graph<>();
        for (int i = 0; i < 100_000; i++) {
            chain.addEdge(i, i + 1, "next");
        }
        assertEquals(100_001, chain.dfs(0).size());   // recursion would overflow the stack
    }

    // ---------------------------------------------------------------------
    // shortest path (student)
    // ---------------------------------------------------------------------

    @Test
    void shortestPathUsesFewestEdges() {
        List<String> path = g.shortestPath("A", "F");
        assertEquals(3, path.size() - 1, "A→B→D→F or A→C→D→F is 3 edges");
        assertEquals("A", path.get(0));
        assertEquals("F", path.get(path.size() - 1));
    }

    @Test
    void shortestPathPrefersFirstDiscoveredRoute() {
        assertEquals(List.of("A", "B", "D", "F"), g.shortestPath("A", "F"));
    }

    @Test
    void shortestPathToSelfIsSingleVertex() {
        assertEquals(List.of("A"), g.shortestPath("A", "A"));
    }

    @Test
    void shortestPathToUnreachableIsEmpty() {
        assertEquals(List.of(), g.shortestPath("A", "G"));
        assertEquals(List.of(), g.shortestPath("A", "missing"));
    }
}
