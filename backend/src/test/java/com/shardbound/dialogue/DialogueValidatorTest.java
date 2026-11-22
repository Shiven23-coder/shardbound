package com.shardbound.dialogue;

import com.shardbound.content.JsonContentReader;
import com.shardbound.content.RawContent;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.DialogueDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.EntryDef;
import com.shardbound.content.RawContent.NodeDef;
import com.shardbound.content.RawContent.RuleDef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DialogueValidator. It traverses with Graph.bfs from every dialogue's first
 * nodes (the viva algorithms, docs/STUDY_GUIDE.md).
 */
class DialogueValidatorTest {

    // ---------------------------------------------------------------------
    // a tiny valid content set: dialogue "d" opens on p01;
    // p01 → p02 → END_QUICK, p01 → res → END_A, p01 ends the dialogue
    // ---------------------------------------------------------------------

    private final List<NodeDef> nodes = new ArrayList<>(List.of(
            node("p01", "TEXT"), node("p02", "TEXT"), node("res", "RESOLVER"),
            node("END_QUICK", "ENDING"), node("END_A", "ENDING")));
    private final List<ChoiceDef> choices = new ArrayList<>(List.of(
            choice(1, "p01", "p02"), choice(2, "p01", "res"), choice(3, "p02", "END_QUICK"), choice(4, "p01", null)));
    private final List<DialogueDef> dialogues = new ArrayList<>(List.of(new DialogueDef("d", null, "p01", List.of())));
    private final List<EndingDef> endings = new ArrayList<>(List.of(
            new EndingDef("END_A", "END_A", "A", 1, true, List.of()),
            new EndingDef("END_QUICK", "END_QUICK", "Quick", 99, false, List.of())));
    private Set<String> flagsReadByWorld = Set.of();
    private Set<String> gateFlags = Set.of();

    private static NodeDef node(String id, String type) {
        return new NodeDef(id, "d", type, id, null, "text", null, null, null, null);
    }

    /** A choice to {@code to}, or one that ends the dialogue when {@code to} is null. */
    private static ChoiceDef choice(int id, String from, @Nullable String to, RuleDef... effects) {
        return new ChoiceDef(id, from, to, "go " + to, id, false, List.of(), List.of(effects));
    }

    private RawContent content() {
        return new RawContent(
                List.of(new RawContent.FactionDef("ELDMERE", "E"), new RawContent.FactionDef("SYLVAN", "S"),
                        new RawContent.FactionDef("CROWN", "C"), new RawContent.FactionDef("ASHEN", "A")),
                List.of(), new RawContent.AffinityCues("{name} will remember that.", "{name} will not forget that."),
                List.of(), List.of(), List.of(),
                List.of(new RawContent.QuestDef("q1", "Q1", "d", null, null, 0, 0, false, List.of()),
                        new RawContent.QuestDef("q2", "Q2", "d", null, null, 0, 0, false, List.of("q1"))),
                List.of(), endings, List.of(), dialogues, nodes, choices);
    }

    private DialogueValidator run() {
        DialogueValidator v = new DialogueValidator(content(), flagsReadByWorld, Set.of(), gateFlags);
        v.validate();
        return v;
    }

    private static boolean mentions(List<String> lines, String text) {
        return lines.stream().anyMatch(line -> line.contains(text));
    }

    // ---------------------------------------------------------------------

    @Test
    void validContentHasNoErrors() {
        DialogueValidator v = run();
        assertTrue(v.errors().isEmpty(), v.errors().toString());
    }

    @Test
    void realContentPassesWithoutErrors() {
        DialogueValidator v = new DialogueValidator(new JsonContentReader(new File("../content")).read(),
                Set.of(), Set.of("storm_night"), Set.of("following_corvin"));
        assertTrue(v.validate(), v.errors().toString());
    }

    @Test
    void check1UnreachableNode() {
        nodes.add(node("island", "ENDING"));
        assertTrue(mentions(run().errors(), "island cannot be reached"));
    }

    @Test
    void check1AnEntryMakesItsNodeReachable() {
        nodes.add(node("second_door", "TEXT"));
        choices.add(choice(5, "second_door", null));
        dialogues.set(0, new DialogueDef("d", null, "p01", List.of(new EntryDef("second_door", List.of()))));
        assertFalse(mentions(run().errors(), "second_door"));
    }

    @Test
    void check2DeadEnd() {
        nodes.add(node("stuck", "TEXT"));
        choices.add(choice(5, "p02", "stuck"));
        assertTrue(mentions(run().errors(), "stuck (TEXT) has no way out"));
    }

    @Test
    void check2EndingTheDialogueIsAWayOut() {
        nodes.add(node("goodbye", "TEXT"));
        choices.add(choice(5, "p02", "goodbye"));
        choices.add(choice(6, "goodbye", null));
        assertTrue(run().errors().isEmpty());
    }

    @Test
    void check3CombatWithoutWinLose() {
        nodes.add(new NodeDef("fight", "d", "COMBAT", "f", null, "t", "enc", null, null, null));
        choices.add(choice(5, "p01", "fight"));
        assertTrue(mentions(run().errors(), "COMBAT node fight needs"));
    }

    @Test
    void check5MissingTargetUnknownItemAndMissingStart() {
        choices.add(choice(5, "p02", "nowhere", new RuleDef("GIVE_ITEM", "ghost_item", null, 1, 0)));
        dialogues.add(new DialogueDef("broken", null, "no_such_node", List.of()));
        DialogueValidator v = run();
        assertTrue(mentions(v.errors(), "points to missing node nowhere"));
        assertTrue(mentions(v.errors(), "Unknown item 'ghost_item'"));
        assertTrue(mentions(v.errors(), "no_such_node referenced by start of dialogue broken"));
    }

    @Test
    void checks6And7FlagWarnings() {                            // D12
        choices.set(0, new ChoiceDef(1, "p01", "p02", "go", 0, false,
                List.of(new RuleDef("FLAG", "never_set", "=", 1, 0)),
                List.of(new RuleDef("SET_FLAG", "never_read", null, 1, 0))));
        DialogueValidator v = run();
        assertTrue(mentions(v.warnings(), "never_set is read but never set"));
        assertTrue(mentions(v.warnings(), "never_read is set but never read"));
        assertTrue(v.errors().isEmpty(), "flag problems are warnings, not errors");
    }

    @Test
    void check7FlagsTheWorldReadsCount() {
        choices.add(choice(5, "p01", "p02", new RuleDef("SET_FLAG", "dawn", null, 1, 0)));
        flagsReadByWorld = Set.of("dawn");                          // e.g. a map object's showIf
        assertFalse(mentions(run().warnings(), "dawn is set but never read"));
    }

    @Test
    void aGateWhoseFlagIsNeverSetIsAnError() {                  // P-45
        gateFlags = Set.of("forest_open");
        assertTrue(mentions(run().errors(), "forest_open"));
        choices.add(choice(5, "p01", "p02", new RuleDef("SET_FLAG", "forest_open", null, 1, 0)));
        assertFalse(mentions(run().errors(), "forest_open"));
    }

    @Test
    void check8ReservedFlag() {                                 // D15
        choices.add(choice(5, "p01", "p02", new RuleDef("SET_FLAG", "_locked_passed", null, 9, 0)));
        assertTrue(mentions(run().errors(), "reserved flag _locked_passed"));
    }

    @Test
    void check9QuestTransitions() {                             // D14
        choices.add(choice(5, "p01", "p02", new RuleDef("COMPLETE_QUEST", "q1", null, 0, 0)));
        choices.add(choice(6, "p01", "p02", new RuleDef("START_QUEST", "q2", null, 0, 0)));
        DialogueValidator v = run();
        assertTrue(mentions(v.errors(), "q1 is completed/failed"));
        assertFalse(mentions(v.errors(), "q2 is started"), "q1 IS completed somewhere, so q2 can unlock");
    }
}
