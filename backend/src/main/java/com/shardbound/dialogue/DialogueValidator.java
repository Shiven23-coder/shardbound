package com.shardbound.dialogue;

import com.shardbound.content.RawContent;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.DialogueDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.EntryDef;
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
 * Checks dialogue content at startup, on raw rows before any objects exist.
 *
 * <p>Reachability runs on the STRUCTURAL graph (D10): choice edges plus
 * COMBAT → win/lose/flee and RESOLVER → resolver endings, starting from every
 * dialogue's first nodes (its {@code start} and entries). Conditions are
 * ignored here; QuickRouteApiIT plays the quick route through the world with
 * real conditions. Which world objects start which dialogues is checked by
 * {@link com.shardbound.world.MapValidator}.
 *
 * <p>Errors block startup; warnings are logged only.
 */
public class DialogueValidator {

    private static final Set<String> CONDITION_TYPES = Set.of("FLAG", "REP", "AFFINITY", "ITEM", "QUEST", "STAT");
    private static final Set<String> EFFECT_TYPES = Set.of("SET_FLAG", "REP", "AFFINITY", "GIVE_ITEM", "TAKE_ITEM",
            "START_QUEST", "COMPLETE_QUEST", "FAIL_QUEST", "HEAL", "GOLD", "SHARDS", "LEARN_SKILL");
    private static final Set<String> OPS = Set.of("=", "!=", ">", ">=", "<", "<=");

    private final RawContent raw;
    private final Set<String> flagsReadByWorld;
    private final Set<String> flagsSetByWorld;
    private final Set<String> gateFlags;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    // id sets built once, O(1) lookups afterwards
    private final Map<String, NodeDef> nodes = new HashMap<>();
    private final Set<String> dialogues = new HashSet<>();
    private final Set<String> items = new HashSet<>();
    private final Set<String> quests = new HashSet<>();
    private final Set<String> factions = new HashSet<>();
    private final Set<String> npcs = new HashSet<>();
    private final Set<String> encounters = new HashSet<>();

    /**
     * @param flagsReadByWorld flags map objects read ({@code showIf}, {@code hideIf}) and gates
     * @param flagsSetByWorld  flags the game's start sets
     * @param gateFlags        flags that open or close a way (obstacles, edge gates): each must be set somewhere (P-45)
     */
    public DialogueValidator(RawContent raw, Set<String> flagsReadByWorld, Set<String> flagsSetByWorld,
                             Set<String> gateFlags) {
        this.raw = raw;
        this.flagsReadByWorld = Set.copyOf(flagsReadByWorld);
        this.flagsSetByWorld = Set.copyOf(flagsSetByWorld);
        this.gateFlags = Set.copyOf(gateFlags);
    }

    /**
     * Runs all checks. Returns true if there are no errors.
     * Time O(V + E + R) for V nodes, E edges and R condition/effect rows.
     */
    public boolean validate() {
        indexIds();
        Graph<String, String> graph = DialogueGraph.structureOf(raw);
        List<String> roots = roots();
        Set<String> reachable = checkReachable(graph, roots);    // 1
        checkDeadEnds();                                          // 2
        checkCombatNodes();                                       // 3
        checkEndingsReachable(reachable);                         // 4
        checkReferences();                                        // 5
        checkFlags();                                             // 6, 7, 8
        checkQuestTransitions();                                  // 9
        return errors.isEmpty();
    }

    public List<String> errors()   { return errors; }
    public List<String> warnings() { return warnings; }

    // ---------------------------------------------------------------------
    // indexes (also catches duplicate ids)
    // ---------------------------------------------------------------------

    private void indexIds() {
        for (NodeDef n : raw.nodes()) {
            if (nodes.put(n.id(), n) != null) errors.add("Duplicate node id: " + n.id());
        }
        for (var d : raw.dialogues()) addUnique(dialogues, d.id(), "dialogue");
        for (var i : raw.items()) addUnique(items, i.id(), "item");
        for (var q : raw.quests()) addUnique(quests, q.id(), "quest");
        for (var f : raw.factions()) addUnique(factions, f.id(), "faction");
        for (var n : raw.npcs()) addUnique(npcs, n.id(), "NPC");
        for (var e : raw.encounters()) addUnique(encounters, e.id(), "encounter");
    }

    private void addUnique(Set<String> set, String id, String kind) {
        if (!set.add(id)) {
            errors.add("Duplicate " + kind + " id: " + id);
        }
    }

    /** Every node a dialogue can open on. */
    private List<String> roots() {
        List<String> roots = new ArrayList<>();
        for (DialogueDef d : raw.dialogues()) {
            roots.add(d.start());
            for (EntryDef entry : d.entries()) {
                roots.add(entry.node());
            }
        }
        return roots;
    }

    // ---------------------------------------------------------------------
    // 1. reachability (BFS from every dialogue's first nodes)
    // ---------------------------------------------------------------------

    private Set<String> checkReachable(Graph<String, String> graph, List<String> roots) {
        Set<String> reachable = new HashSet<>();
        for (String root : roots) {
            if (nodes.containsKey(root)) {
                reachable.addAll(graph.bfs(root));
            }
        }
        for (NodeDef node : raw.nodes()) {
            if (!reachable.contains(node.id())) {
                errors.add("Node " + node.id() + " cannot be reached from any dialogue's first node (check 1)");
            }
        }
        return reachable;
    }

    // ---------------------------------------------------------------------
    // 2. dead ends: every node but an ending offers a way on
    // ---------------------------------------------------------------------

    private void checkDeadEnds() {
        Set<String> withChoices = new HashSet<>();
        for (ChoiceDef c : raw.choices()) {
            withChoices.add(c.fromNode());
        }
        for (NodeDef n : raw.nodes()) {
            boolean leads = withChoices.contains(n.id()) || "COMBAT".equals(n.type()) || "RESOLVER".equals(n.type());
            if (!"ENDING".equals(n.type()) && !leads) {
                errors.add("Node " + n.id() + " (" + n.type() + ") has no way out (check 2)");
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
                warnings.add("Ending " + ending.id() + " cannot be reached from any dialogue (check 4)");
            }
        }
    }

    // ---------------------------------------------------------------------
    // 5. references
    // ---------------------------------------------------------------------

    private void checkReferences() {
        for (DialogueDef d : raw.dialogues()) {
            String where = "dialogue " + d.id();
            refNode(d.start(), "start of " + where);
            refOpt(npcs, d.npc(), "NPC", where);
            for (EntryDef entry : d.entries()) {
                refNode(entry.node(), "entry of " + where);
                for (RuleDef r : entry.conditions()) checkCondition(r, "entry of " + where);
            }
        }
        for (NodeDef n : raw.nodes()) {
            refOpt(npcs, n.speaker(), "NPC", "speaker of node " + n.id());
            refOpt(encounters, n.encounterId(), "encounter", "node " + n.id());
            refNode(n.winNode(), "win of " + n.id());
            refNode(n.loseNode(), "lose of " + n.id());
            refNode(n.fleeNode(), "flee of " + n.id());
        }
        for (ChoiceDef c : raw.choices()) {
            String where = "choice '" + c.label() + "' on " + c.fromNode();
            refNode(c.fromNode(), where);
            if (c.toNode() != null && !nodes.containsKey(c.toNode())) {
                errors.add(where + " points to missing node " + c.toNode() + " (check 5)");
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
            default -> { }   // FLAG keys are open (checks 6–8)
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
            errors.add("Unknown " + kind + " '" + id + "' in " + where + " (check 5)");
        }
    }

    private void refOpt(Set<String> ids, String id, String kind, String where) {
        if (id != null) {
            ref(ids, id, kind, where);
        }
    }

    private void refNode(String id, String where) {
        if (id != null && !nodes.containsKey(id)) {
            errors.add("Missing node " + id + " referenced by " + where + " (check 5)");
        }
    }

    // ---------------------------------------------------------------------
    // 6, 7, 8. flags (set differences with HashSets, D12, D15)
    // ---------------------------------------------------------------------

    private void checkFlags() {
        Set<String> read = new TreeSet<>(flagsReadByWorld);
        Set<String> written = new TreeSet<>(flagsSetByWorld);
        for (ChoiceDef c : raw.choices()) {
            collectFlagReads(c.conditions(), read);
            for (RuleDef r : c.effects()) {
                if ("SET_FLAG".equals(r.type())) {
                    if (r.key().startsWith("_")) {
                        errors.add("Content may not SET_FLAG reserved flag " + r.key() + " (check 8, D15)");
                    } else {
                        written.add(r.key());
                    }
                }
            }
        }
        for (DialogueDef d : raw.dialogues()) {
            for (EntryDef entry : d.entries()) collectFlagReads(entry.conditions(), read);
        }
        for (EndingDef e : raw.endings()) {
            collectFlagReads(e.conditions(), read);
        }
        for (RawContent.ObjectiveDef o : raw.objectives()) {
            collectFlagReads(o.conditions(), read);
        }
        for (String flag : read) {
            if (!flag.startsWith("_") && !written.contains(flag)) {
                warnings.add("Flag " + flag + " is read but never set (check 6)");
            }
        }
        for (String flag : written) {
            if (!read.contains(flag)) {
                warnings.add("Flag " + flag + " is set but never read (check 7)");
            }
        }
        for (String flag : new TreeSet<>(gateFlags)) {
            if (!written.contains(flag)) {
                errors.add("Flag " + flag + " opens or closes a way but is never set in the content (P-45)");
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
    // 9. quest transitions detectable without playing (D14)
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
        for (String[] e : endsWithoutStart) {
            if (quests.contains(e[0]) && !started.contains(e[0])) {
                errors.add("Quest " + e[0] + " is completed/failed in " + e[1] + " but never started (check 9)");
            }
        }
        Map<String, List<String>> prereqs = new HashMap<>();
        for (var q : raw.quests()) prereqs.put(q.id(), q.prereqs());
        for (String[] s : starts) {
            for (String prereq : prereqs.getOrDefault(s[0], List.of())) {
                if (!completed.contains(prereq)) {
                    errors.add("Quest " + s[0] + " is started in " + s[1] + " but its prerequisite " + prereq
                            + " is never completed (check 9)");
                }
            }
        }
    }
}
