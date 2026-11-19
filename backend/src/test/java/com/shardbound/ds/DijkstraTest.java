package com.shardbound.ds;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Graph.dijkstra (viva algorithm, docs/STUDY_GUIDE.md §6). Edge values are the weights.
 * <pre>
 *   A → B 4   A → C 1   C → B 2   B → D 1   C → D 5   D → E 3      F isolated
 * </pre>
 * The direct edge A → B (4) loses to A → C → B (1 + 2 = 3).
 */
class DijkstraTest {

    private Graph<String, Integer> g;

    @BeforeEach
    void setUp() {
        g = new Graph<>();
        g.addEdge("A", "B", 4);
        g.addEdge("A", "C", 1);
        g.addEdge("C", "B", 2);
        g.addEdge("B", "D", 1);
        g.addEdge("C", "D", 5);
        g.addEdge("D", "E", 3);
        g.addVertex("F");
    }

    @Test
    void distancesUseTheCheapestRouteNotTheFewestEdges() {
        ShortestPaths<String> sp = g.dijkstra("A", w -> w);
        assertEquals(0, sp.distanceTo("A"));
        assertEquals(1, sp.distanceTo("C"));
        assertEquals(3, sp.distanceTo("B"));       // via C, not the direct 4
        assertEquals(4, sp.distanceTo("D"));       // via C, B (not C → D = 6)
        assertEquals(7, sp.distanceTo("E"));
    }

    @Test
    void pathIsRebuiltFromParents() {
        assertEquals(List.of("A", "C", "B", "D", "E"), g.dijkstra("A", w -> w).pathTo("E"));
        assertEquals(List.of("A"), g.dijkstra("A", w -> w).pathTo("A"));
    }

    @Test
    void unreachableVerticesAreAbsent() {
        ShortestPaths<String> sp = g.dijkstra("A", w -> w);
        assertFalse(sp.reachable("F"));
        assertEquals(-1, sp.distanceTo("F"));
        assertEquals(List.of(), sp.pathTo("F"));
        assertEquals(5, sp.distances().size());
    }

    @Test
    void unknownSourceGivesAnEmptyResult() {
        ShortestPaths<String> sp = g.dijkstra("Z", w -> w);
        assertTrue(sp.distances().isEmpty());
        assertEquals(List.of(), sp.pathTo("A"));
    }

    @Test
    void zeroWeightEdgesAreAllowed() {
        g.addEdge("E", "F", 0);
        ShortestPaths<String> sp = g.dijkstra("A", w -> w);
        assertEquals(7, sp.distanceTo("F"));
        assertEquals(List.of("A", "C", "B", "D", "E", "F"), sp.pathTo("F"));
    }

    @Test
    void tiesKeepTheFirstFoundParent() {
        Graph<String, Integer> tie = new Graph<>();
        tie.addEdge("S", "X", 1);
        tie.addEdge("S", "Y", 1);
        tie.addEdge("X", "Z", 1);
        tie.addEdge("Y", "Z", 1);
        assertEquals(List.of("S", "X", "Z"), tie.dijkstra("S", w -> w).pathTo("Z"));
    }

    @Test
    void negativeWeightIsRejected() {
        g.addEdge("E", "A", -1);
        assertThrows(IllegalArgumentException.class, () -> g.dijkstra("A", w -> w));
    }

    @Test
    void weightFunctionCanMapEdgeValues() {
        Graph<String, String> labelled = new Graph<>();
        labelled.addEdge("gate", "market", "30s");
        labelled.addEdge("market", "palace", "45s");
        ShortestPaths<String> sp = labelled.dijkstra("gate", label -> Integer.parseInt(label.replace("s", "")));
        assertEquals(75, sp.distanceTo("palace"));
    }

    @Test
    void cyclesDoNotLoop() {
        g.addEdge("E", "A", 2);
        assertEquals(7, g.dijkstra("A", w -> w).distanceTo("E"));
    }
}
