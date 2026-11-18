package com.shardbound.story;

import com.shardbound.content.RawContent;
import com.shardbound.ds.Graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The story as a graph, held in memory for the whole run (spec §5):
 * <ul>
 *   <li>{@code HashMap<String, StoryNode>} — O(1) node lookup by id</li>
 *   <li>{@code HashMap<String, List<Choice>>} — adjacency list of choices, O(k) per node</li>
 *   <li>{@code Graph<String, String>} — the STRUCTURAL graph (D10): choice edges
 *       plus COMBAT → win/lose/flee and RESOLVER → every resolver ending, used
 *       by StoryValidator and GraphExporter</li>
 * </ul>
 * Immutable after construction and shared by every session.
 */
public class StoryGraph {

    public static final String START_NODE = "p01";
    public static final String QUICK_ENDING_NODE = "END_QUICK";

    private final Map<String, StoryNode> nodes;
    private final Map<String, List<Choice>> choicesByNode;
    private final Graph<String, String> structure;

    /**
     * @param resolverEndingNodes node ids of endings with via_resolver = true,
     *                            in priority order (structural RESOLVER edges)
     * Time O(V + E) to build, space O(V + E).
     */
    public StoryGraph(Map<String, StoryNode> nodes, List<Choice> choices, List<String> resolverEndingNodes) {
        this.nodes = Collections.unmodifiableMap(new HashMap<>(nodes));
        this.choicesByNode = groupChoices(choices);
        this.structure = new Graph<>();
        for (StoryNode node : nodes.values()) {
            structure.addVertex(node.id());
        }
        for (Choice choice : sortedChoices(choices)) {
            structure.addEdge(choice.from(), choice.to(), "choice " + choice.id());
        }
        addStructuralEdges(structure, nodes.values(), resolverEndingNodes);
    }

    /** Node by id, or null. O(1). */
    public StoryNode node(String id) {
        return nodes.get(id);
    }

    /** All nodes (read-only). */
    public Map<String, StoryNode> nodes() {
        return nodes;
    }

    /** Choices leaving a node in display order (read-only; empty if none). O(1). */
    public List<Choice> choicesFrom(String nodeId) {
        return choicesByNode.getOrDefault(nodeId, List.of());
    }

    /** The choice with this id on this node, if it exists. O(k) for k choices on the node. */
    public Optional<Choice> choice(String nodeId, int choiceId) {
        for (Choice choice : choicesFrom(nodeId)) {
            if (choice.id() == choiceId) {
                return Optional.of(choice);
            }
        }
        return Optional.empty();
    }

    /** Choice by id anywhere in the graph (journal labels), or null. O(E). */
    public Choice choiceById(int choiceId) {
        for (List<Choice> list : choicesByNode.values()) {
            for (Choice choice : list) {
                if (choice.id() == choiceId) {
                    return choice;
                }
            }
        }
        return null;
    }

    /** The structural graph (D10). */
    public Graph<String, String> structure() {
        return structure;
    }

    public int choiceCount() {
        int count = 0;
        for (List<Choice> list : choicesByNode.values()) {
            count += list.size();
        }
        return count;
    }

    // ---------------------------------------------------------------------
    // Building the structural graph straight from raw rows (used by the
    // validator before any Java objects exist). Time O(V + E).
    // ---------------------------------------------------------------------

    /** Structural graph from raw content: same edges as the constructor builds. */
    public static Graph<String, String> structureOf(RawContent raw) {
        Graph<String, String> graph = new Graph<>();
        for (RawContent.NodeDef node : raw.nodes()) {
            graph.addVertex(node.id());
        }
        List<RawContent.ChoiceDef> choices = new ArrayList<>(raw.choices());
        choices.sort(Comparator.comparing(RawContent.ChoiceDef::fromNode)
                .thenComparingInt(RawContent.ChoiceDef::sortOrder));
        for (RawContent.ChoiceDef choice : choices) {
            graph.addEdge(choice.fromNode(), choice.toNode(), "choice " + choice.id());
        }
        List<String> resolverEndings = new ArrayList<>();
        List<RawContent.EndingDef> endings = new ArrayList<>(raw.endings());
        endings.sort(Comparator.comparingInt(RawContent.EndingDef::priority));
        for (RawContent.EndingDef ending : endings) {
            if (ending.viaResolver()) {
                resolverEndings.add(ending.nodeId());
            }
        }
        for (RawContent.NodeDef node : raw.nodes()) {
            addEdgesFor(graph, node.id(), node.type(), node.winNode(), node.loseNode(), node.fleeNode(), resolverEndings);
        }
        return graph;
    }

    private static void addStructuralEdges(Graph<String, String> graph, Iterable<StoryNode> nodes,
                                           List<String> resolverEndings) {
        for (StoryNode node : nodes) {
            addEdgesFor(graph, node.id(), node.type().name(), node.winNode(), node.loseNode(), node.fleeNode(),
                    resolverEndings);
        }
    }

    /** COMBAT → win/lose/flee; RESOLVER → every resolver ending (D10). */
    private static void addEdgesFor(Graph<String, String> graph, String id, String type, String win, String lose,
                                    String flee, List<String> resolverEndings) {
        if ("COMBAT".equals(type)) {
            if (win != null) graph.addEdge(id, win, "win");
            if (lose != null) graph.addEdge(id, lose, "lose");
            if (flee != null) graph.addEdge(id, flee, "flee");
        } else if ("RESOLVER".equals(type)) {
            for (String endingNode : resolverEndings) {
                graph.addEdge(id, endingNode, "resolver");
            }
        }
    }

    private static Map<String, List<Choice>> groupChoices(List<Choice> choices) {
        Map<String, List<Choice>> grouped = new HashMap<>();
        for (Choice choice : sortedChoices(choices)) {
            grouped.computeIfAbsent(choice.from(), k -> new ArrayList<>()).add(choice);
        }
        Map<String, List<Choice>> frozen = new HashMap<>();
        for (Map.Entry<String, List<Choice>> entry : grouped.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static List<Choice> sortedChoices(List<Choice> choices) {
        List<Choice> sorted = new ArrayList<>(choices);
        sorted.sort(Comparator.comparing(Choice::from).thenComparingInt(Choice::sortOrder));
        return sorted;
    }
}
