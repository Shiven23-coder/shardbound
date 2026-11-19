package com.shardbound.story;

import com.shardbound.content.RawContent;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.NodeDef;
import com.shardbound.content.RawContent.RuleDef;
import com.shardbound.ds.Graph;
import com.shardbound.rules.StatCondition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks content before it is imported (spec §8.4). Works on raw rows, so it
 * can run on JSON files before anything touches the database.
 *
 * <p>Reachability checks run on the STRUCTURAL graph (D10): choice edges plus
 * COMBAT → win/lose/flee and RESOLVER → resolver endings. Conditions are
 * ignored here; QuickRouteApiIT plays the quick route with real
 * conditions.
 *
 * <p>Errors block the import; warnings are printed only.
 */
public class StoryValidator {

    public static final int QUICK_ROUTE_MAX_STEPS = 12;
    /** Check 4 becomes an error once Chapters 1–5 exist (Phase 9). */
    public static final boolean ENDINGS_UNREACHABLE_IS_ERROR = false;

    private static final Set<String> CONDITION_TYPES = Set.of("FLAG", "REP", "AFFINITY", "ITEM", "QUEST", "STAT");
    private static final Set<String> EFFECT_TYPES = Set.of("SET_FLAG", "REP", "AFFINITY", "GIVE_ITEM", "TAKE_ITEM",
            "START_QUEST", "COMPLETE_QUEST", "FAIL_QUEST", "HEAL", "GOLD", "XP");
    private static final Set<String> OPS = Set.of("=", "!=", ">", ">=", "<", "<=");

    private final RawContent raw;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> info = new ArrayList<>();

    // id sets built once, O(1) lookups afterwards
    private final Map<String, NodeDef> nodes = new HashMap<>();
    private final Set<String> items = new HashSet<>();
    private final Set<String> quests = new HashSet<>();
    private final Set<String> factions = new HashSet<>();
    private final Set<String> npcs = new HashSet<>();
    private final Set<String> locations = new HashSet<>();
    private final Set<String> encounters = new HashSet<>();
    private final Set<String> enemies = new HashSet<>();

    public StoryValidator(RawContent raw) {
        this.raw = raw;
    }

    /**
     * Runs all checks. Returns true if there are no errors.
     * Time O(V + E + R) for V nodes, E edges and R condition/effect rows.
     */
    public boolean validate() {
        indexIds();
        Graph<String, String> graph = StoryGraph.structureOf(raw);
        Set<String> reachable = checkReachable(graph);           // 1
        checkDeadEnds(graph);                                    // 2
        checkCombatNodes();                                      // 3
        checkEndingsReachable(reachable);                        // 4
        checkQuickRoute(graph);                                  // 5
        checkReferences();                                       // 6
        checkFlags();                                            // 7, 8, 9
        checkQuestTransitions();                                 // 10
        info.add(raw.nodes().size() + " nodes, " + raw.choices().size() + " choices, "
                + raw.endings().size() + " endings, " + raw.quests().size() + " quests");
        return errors.isEmpty();
    }

    public List<String> errors()   { return errors; }
    public List<String> warnings() { return warnings; }
    public List<String> info()     { return info; }

    /** Human-readable report for the console. */
    public String report() {
        StringBuilder sb = new StringBuilder();
        for (String line : info) sb.append("  info:    ").append(line).append('\n');
        for (String line : warnings) sb.append("  WARNING: ").append(line).append('\n');
        for (String line : errors) sb.append("  ERROR:   ").append(line).append('\n');
        sb.append("  => ").append(errors.size()).append(" error(s), ").append(warnings.size()).append(" warning(s)");
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // indexes (also catches duplicate ids)
    // ---------------------------------------------------------------------

    private void indexIds() {
        for (NodeDef n : raw.nodes()) {
            if (nodes.put(n.id(), n) != null) errors.add("Duplicate node id: " + n.id());
        }
        for (var i : raw.items()) addUnique(items, i.id(), "item");
        for (var q : raw.quests()) addUnique(quests, q.id(), "quest");
        for (var f : raw.factions()) addUnique(factions, f.id(), "faction");
        for (var n : raw.npcs()) addUnique(npcs, n.id(), "NPC");
        for (var l : raw.locations()) addUnique(locations, l.id(), "location");
        for (var e : raw.encounters()) addUnique(encounters, e.id(), "encounter");
        for (var e : raw.enemies()) addUnique(enemies, e.id(), "enemy");
    }

    private void addUnique(Set<String> set, String id, String kind) {
        if (!set.add(id)) {
            errors.add("Duplicate " + kind + " id: " + id);
        }
    }

    // ---------------------------------------------------------------------
    // 1. reachability (BFS)
    // ---------------------------------------------------------------------

    private Set<String> checkReachable(Graph<String, String> graph) {
        if (!nodes.containsKey(StoryGraph.START_NODE)) {
            errors.add("Start node " + StoryGraph.START_NODE + " does not exist");
            return Set.of();
        }
        Set<String> reachable = new HashSet<>(graph.bfs(StoryGraph.START_NODE));
        for (NodeDef node : raw.nodes()) {
            if (!reachable.contains(node.id())) {
                errors.add("Node " + node.id() + " is unreachable from " + StoryGraph.START_NODE + " (check 1)");
            }
        }
        return reachable;
    }

    // ---------------------------------------------------------------------
    // 2. dead ends (DFS over the reachable part)
    // ---------------------------------------------------------------------

    private void checkDeadEnds(Graph<String, String> graph) {
        if (!nodes.containsKey(StoryGraph.START_NODE)) {
            return;
        }
        for (String id : graph.dfs(StoryGraph.START_NODE)) {
            NodeDef node = nodes.get(id);
            if (node != null && !"ENDING".equals(node.type()) && graph.edgesFrom(id).isEmpty()) {
                errors.add("Node " + id + " (" + node.type() + ") has no way out (check 2)");
            }
        }
    }

    // ---------------------------------------------------------------------
    // 3. combat nodes
    // ---------------------------------------------------------------------

    private void checkCombatNodes() {
        for (NodeDef n : raw.nodes()) {
            boolean combat = "COMBAT".equals(n.type());
            if (combat && (n.encounterId() == null || n.winNode() == null || n.loseNode() == null)) {
                errors.add("COMBAT node " + n.id() + " needs encounter, win and lose (check 3)");
            }
            if (!combat && (n.encounterId() != null || n.winNode() != null || n.loseNode() != null
                    || n.fleeNode() != null)) {
                errors.add("Node " + n.id() + " is not COMBAT but sets encounter/win/lose/flee (check 3, D9)");
            }
        }
    }

    // ---------------------------------------------------------------------
    // 4. endings reachable
    // ---------------------------------------------------------------------

    private void checkEndingsReachable(Set<String> reachable) {
        for (EndingDef ending : raw.endings()) {
            if (!reachable.contains(ending.nodeId())) {
                String message = "Ending " + ending.id() + " is unreachable from the start (check 4)";
                if (ENDINGS_UNREACHABLE_IS_ERROR) errors.add(message); else warnings.add(message);
            }
        }
    }

    // ---------------------------------------------------------------------
    // 5. quick route length (BFS shortest path)
    // ---------------------------------------------------------------------

    private void checkQuickRoute(Graph<String, String> graph) {
        String target = StoryGraph.QUICK_ENDING_NODE;
        if (!nodes.containsKey(StoryGraph.START_NODE) || !nodes.containsKey(target)) {
            errors.add("Quick ending node " + target + " is missing (check 5)");
            return;
        }
        List<String> path = graph.shortestPath(StoryGraph.START_NODE, target);
        if (path.isEmpty()) {
            errors.add(target + " cannot be reached from " + StoryGraph.START_NODE + " (check 5)");
            return;
        }
        int steps = path.size() - 1;
        info.add("Quick route: " + steps + " steps (" + String.join(" → ", path) + ")");
        if (steps > QUICK_ROUTE_MAX_STEPS) {
            errors.add("Quick route is " + steps + " steps; the maximum is " + QUICK_ROUTE_MAX_STEPS + " (check 5)");
        }
    }

    // ---------------------------------------------------------------------
    // 6. references
    // ---------------------------------------------------------------------

    private void checkReferences() {
        for (NodeDef n : raw.nodes()) {
            ref(locations, n.locationId(), "location", "node " + n.id());
            refOpt(encounters, n.encounterId(), "encounter", "node " + n.id());
            refNode(n.winNode(), "win of " + n.id());
            refNode(n.loseNode(), "lose of " + n.id());
            refNode(n.fleeNode(), "flee of " + n.id());
        }
        for (ChoiceDef c : raw.choices()) {
            String where = "choice '" + c.label() + "' on " + c.fromNode();
            refNode(c.fromNode(), where);
            if (c.toNode() == null || !nodes.containsKey(c.toNode())) {
                errors.add(where + " points to missing node " + c.toNode() + " (check 6)");
            }
            for (RuleDef r : c.conditions()) checkCondition(r, where);
            for (RuleDef r : c.effects()) checkEffect(r, where);
        }
        for (EndingDef e : raw.endings()) {
            refNode(e.nodeId(), "ending " + e.id());
            for (RuleDef r : e.conditions()) checkCondition(r, "ending " + e.id());
        }
        for (var q : raw.quests()) {
            refOpt(npcs, q.giverNpc(), "NPC", "quest " + q.id());
            refOpt(items, q.rewardItem(), "item", "quest " + q.id());
            for (String p : q.prereqs()) ref(quests, p, "quest", "prerequisites of " + q.id());
        }
        for (var n : raw.npcs()) {
            refOpt(factions, n.factionId(), "faction", "NPC " + n.id());
            refOpt(locations, n.locationId(), "location", "NPC " + n.id());
        }
        for (var e : raw.encounters()) {
            for (var slot : e.enemies()) ref(enemies, slot.enemyId(), "enemy", "encounter " + e.id());
        }
    }

    private void checkCondition(RuleDef r, String where) {
        if (!CONDITION_TYPES.contains(r.type())) {
            errors.add("Unknown condition type " + r.type() + " in " + where);
            return;
        }
        if (r.op() == null || !OPS.contains(r.op())) {
            errors.add("Bad operator '" + r.op() + "' in " + where);
        }
        switch (r.type()) {
            case "REP" -> ref(factions, r.key(), "faction", where);
            case "AFFINITY" -> ref(npcs, r.key(), "NPC", where);
            case "ITEM" -> ref(items, r.key(), "item", where);
            case "QUEST" -> {
                ref(quests, r.key(), "quest", where);
                if (!"=".equals(r.op()) && !"!=".equals(r.op())) {
                    errors.add("QUEST condition in " + where + " must use = or != (spec §6.2)");
                }
            }
            case "STAT" -> {
                if (!StatCondition.STATS.contains(r.key())) errors.add("Unknown stat " + r.key() + " in " + where);
            }
            default -> { }   // FLAG keys are open (checks 7–9)
        }
    }

    private void checkEffect(RuleDef r, String where) {
        if (!EFFECT_TYPES.contains(r.type())) {
            errors.add("Unknown effect type " + r.type() + " in " + where);
            return;
        }
        switch (r.type()) {
            case "REP" -> ref(factions, r.key(), "faction", where);
            case "AFFINITY" -> ref(npcs, r.key(), "NPC", where);
            case "GIVE_ITEM", "TAKE_ITEM" -> ref(items, r.key(), "item", where);
            case "START_QUEST", "COMPLETE_QUEST", "FAIL_QUEST" -> ref(quests, r.key(), "quest", where);
            default -> { }
        }
    }

    private void ref(Set<String> ids, String id, String kind, String where) {
        if (id == null || !ids.contains(id)) {
            errors.add("Unknown " + kind + " '" + id + "' in " + where + " (check 6)");
        }
    }

    private void refOpt(Set<String> ids, String id, String kind, String where) {
        if (id != null) {
            ref(ids, id, kind, where);
        }
    }

    private void refNode(String id, String where) {
        if (id != null && !nodes.containsKey(id)) {
            errors.add("Missing node " + id + " referenced by " + where + " (check 6)");
        }
    }

    // ---------------------------------------------------------------------
    // 7, 8, 9. flags (set differences with HashSets, D12, D15)
    // ---------------------------------------------------------------------

    private void checkFlags() {
        Set<String> read = new TreeSet<>();
        Set<String> written = new TreeSet<>();
        for (ChoiceDef c : raw.choices()) {
            collectFlagReads(c.conditions(), read);
            for (RuleDef r : c.effects()) {
                if ("SET_FLAG".equals(r.type())) {
                    if (r.key().startsWith("_")) {
                        errors.add("Content may not SET_FLAG reserved flag " + r.key() + " (check 9, D15)");
                    } else {
                        written.add(r.key());
                    }
                }
            }
        }
        for (EndingDef e : raw.endings()) {
            collectFlagReads(e.conditions(), read);
        }
        for (String flag : read) {
            if (!flag.startsWith("_") && !written.contains(flag)) {
                warnings.add("Flag " + flag + " is read but never set (check 7)");
            }
        }
        for (String flag : written) {
            if (!read.contains(flag)) {
                warnings.add("Flag " + flag + " is set but never read (check 8)");
            }
        }
    }

    private static void collectFlagReads(List<RuleDef> conditions, Set<String> read) {
        for (RuleDef r : conditions) {
            if ("FLAG".equals(r.type())) {
                read.add(r.key());
            }
        }
    }

    // ---------------------------------------------------------------------
    // 10. quest transitions detectable without playing (D14)
    // ---------------------------------------------------------------------

    private void checkQuestTransitions() {
        Set<String> started = new HashSet<>();
        Set<String> completed = new HashSet<>();
        List<String[]> endsWithoutStart = new ArrayList<>();
        List<String[]> starts = new ArrayList<>();
        for (ChoiceDef c : raw.choices()) {
            String where = "choice '" + c.label() + "' on " + c.fromNode();
            for (RuleDef r : c.effects()) {
                switch (r.type()) {
                    case "START_QUEST" -> { started.add(r.key()); starts.add(new String[]{r.key(), where}); }
                    case "COMPLETE_QUEST" -> { completed.add(r.key()); endsWithoutStart.add(new String[]{r.key(), where}); }
                    case "FAIL_QUEST" -> endsWithoutStart.add(new String[]{r.key(), where});
                    default -> { }
                }
            }
        }
        // (b) completing/failing a quest nothing ever starts
        for (String[] e : endsWithoutStart) {
            if (quests.contains(e[0]) && !started.contains(e[0])) {
                errors.add("Quest " + e[0] + " is completed/failed in " + e[1] + " but never started (check 10)");
            }
        }
        // (c) starting a quest whose prerequisite can never be completed
        Map<String, List<String>> prereqs = new HashMap<>();
        for (var q : raw.quests()) prereqs.put(q.id(), q.prereqs());
        for (String[] s : starts) {
            for (String prereq : prereqs.getOrDefault(s[0], List.of())) {
                if (!completed.contains(prereq)) {
                    errors.add("Quest " + s[0] + " is started in " + s[1] + " but its prerequisite " + prereq
                            + " is never completed (check 10)");
                }
            }
        }
    }
}
