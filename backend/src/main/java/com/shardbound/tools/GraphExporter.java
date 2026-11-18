package com.shardbound.tools;

import com.shardbound.content.ContentAssembler;
import com.shardbound.content.GameContent;
import com.shardbound.content.JsonContentReader;
import com.shardbound.ds.Edge;
import com.shardbound.ds.Graph;
import com.shardbound.story.StoryGraph;
import com.shardbound.story.StoryNode;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Writes the structural story graph as Graphviz DOT (file I/O, spec §5).
 * Turn it into the image for docs with: {@code dot -Tsvg docs/story-graph.dot -o docs/story-graph.svg}.
 *
 * <pre>.\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.tools.GraphExporter</pre>
 * Reads content/*.json directly, so it works without a database.
 */
public class GraphExporter {

    public static void main(String[] args) throws IOException {
        File contentDir = new File("../content").isDirectory() ? new File("../content") : new File("content");
        File out = new File(args.length > 0 ? args[0] : "../docs/story-graph.dot");
        GameContent content = new ContentAssembler().assemble(new JsonContentReader(contentDir).read());
        try (PrintWriter writer = new PrintWriter(out, StandardCharsets.UTF_8)) {
            writer.print(toDot(content.graph()));
        }
        System.out.println("Wrote " + out.getPath());
    }

    /**
     * DOT text: one box per node (shape by type, coloured by chapter), one
     * arrow per structural edge, labelled with its kind. Time O(V + E).
     */
    public static String toDot(StoryGraph story) {
        Graph<String, String> graph = story.structure();
        StringBuilder sb = new StringBuilder();
        sb.append("digraph Shardbound {\n");
        sb.append("  rankdir=LR;\n  node [fontname=\"Helvetica\", fontsize=10];\n  edge [fontsize=8];\n");
        for (String id : graph.vertices()) {
            StoryNode node = story.node(id);
            sb.append("  \"").append(id).append("\" [label=\"").append(id).append("\\n")
              .append(escape(node.title())).append("\", shape=").append(shape(node))
              .append(", style=filled, fillcolor=\"").append(colour(node.chapter())).append("\"];\n");
        }
        for (String from : graph.vertices()) {
            for (Edge<String, String> edge : graph.edgesFrom(from)) {
                String label = edge.value().startsWith("choice") ? "" : edge.value();
                sb.append("  \"").append(from).append("\" -> \"").append(edge.to()).append("\"");
                if (!label.isEmpty()) {
                    sb.append(" [label=\"").append(label).append("\", style=dashed]");
                }
                sb.append(";\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String shape(StoryNode node) {
        return switch (node.type()) {
            case STORY -> "box";
            case COMBAT -> "octagon";
            case RESOLVER -> "diamond";
            case ENDING -> "doubleoctagon";
        };
    }

    private static String colour(int chapter) {
        String[] palette = {"#f6e3c5", "#f2d0a4", "#cfe8c8", "#c9c6e3", "#f3e19c", "#d6ecf5"};
        return palette[Math.max(0, Math.min(chapter, palette.length - 1))];
    }

    private static String escape(String text) {
        return text.replace("\"", "\\\"");
    }
}
