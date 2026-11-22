package com.shardbound.dialogue;

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
 * Every dialogue node as one graph, held in memory for the whole run:
 * <ul>
 *   <li>{@code HashMap<String, DialogueNode>}: O(1) node lookup by id</li>
 *   <li>{@code HashMap<String, List<Choice>>}: adjacency list of choices, O(k) per node</li>
 *   <li>{@code HashMap<String, Dialogue>}: the dialogues world objects start</li>
 *   <li>{@code Graph<String, String>}: the STRUCTURAL graph (D10): choice edges
 *       plus COMBAT → win/lose/flee and RESOLVER → every resolver ending, used
 *       by DialogueValidator</li>
 * </ul>
 * Choices may lead into another dialogue's nodes (the finale is shared).
 * Immutable after construction and shared by every session.
 */
public class DialogueGraph {

    private final Map<String, DialogueNode> nodes;
    private final Map<String, List<Choice>> choicesByNode;
    private final Map<String, Dialogue> dialogues;
    private final Graph<String, String> structure;

    /**
     * @param resolverEndingNodes node ids of endings with via_resolver = true,
     *                            in priority order (structural RESOLVER edges)
     * Time O(V + E) to build, space O(V + E).
     */
    public DialogueGraph(Map<String, DialogueNode> nodes, List<Choice> choices, Map<String, Dialogue> dialogues,
                         List<String> resolverEndingNodes) {
        this.nodes = Collections.unmodifiableMap(new HashMap<>(nodes));
        this.choicesByNode = groupChoices(choices);
        this.dialogues = Collections.unmodifiableMap(new HashMap<>(dialogues));
        this.structure = new Graph<>();
        for (DialogueNode node : nodes.values()) {
            structure.addVertex(node.id());
        }
        for (Choice choice : sortedChoices(choices)) {
            if (!choice.endsDialogue()) {
                structure.addEdge(choice.from(), choice.to(), "choice " + choice.id());
            }
        }
        for (DialogueNode node : nodes.values()) {
            addEdgesFor(structure, node.id(), node.type().name(), node.winNode(), node.loseNode(), node.fleeNode(),
                    resolverEndingNodes);
        }
    }

    /** Node by id, or null. O(1). */
    public DialogueNode node(String id) {
        return nodes.get(id);
    }

    /** All nodes (read-only). */
    public Map<String, DialogueNode> nodes() {
        return nodes;
    }

    /** Dialogue by id, or null. O(1). */
    public Dialogue dialogue(String id) {
        return dialogues.get(id);
    }

    public Map<String, Dialogue> dialogues() {
        return dialogues;
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

    // ---------------------------------------------------------------------
    // The structural graph straight from raw rows (used by the validator
    // before any Java objects exist). Time O(V + E).
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
            if (choice.toNode() != null) {
                graph.addEdge(choice.fromNode(), choice.toNode(), "choice " + choice.id());
            }
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
