package com.shardbound.story;

import com.shardbound.content.JsonContentReader;
import com.shardbound.content.RawContent;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.NodeDef;
import com.shardbound.content.RawContent.RuleDef;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StoryValidator (spec §8.4). It traverses with Graph.bfs / dfs /
 * shortestPath (the viva algorithms, docs/STUDY_GUIDE.md).
 */
class StoryValidatorTest {

    // ---------------------------------------------------------------------
    // a tiny valid story:  p01 → p02 → END_QUICK,  p01 → res → END_A
    // ---------------------------------------------------------------------

    private final List<NodeDef> nodes = new ArrayList<>(List.of(
            node("p01", "STORY"), node("p02", "STORY"), node("res", "RESOLVER"),
            node("END_QUICK", "ENDING"), node("END_A", "ENDING")));
    private final List<ChoiceDef> choices = new ArrayList<>(List.of(
            choice(1, "p01", "p02"), choice(2, "p01", "res"), choice(3, "p02", "END_QUICK")));
    private final List<EndingDef> endings = new ArrayList<>(List.of(
            new EndingDef("END_A", "END_A", "A", 1, true, List.of()),
            new EndingDef("END_QUICK", "END_QUICK", "Quick", 99, false, List.of())));

    private static NodeDef node(String id, String type) {
        return new NodeDef(id, 0, "loc", type, id, "text", null, null, null, null);
    }

    private static ChoiceDef choice(int id, String from, String to, RuleDef... effects) {
        return new ChoiceDef(id, from, to, "go " + to, id, false, List.of(), List.of(effects));
    }

    private RawContent content() {
        return new RawContent(
                List.of(new RawContent.FactionDef("ELDMERE", "E"), new RawContent.FactionDef("SYLVAN", "S"),
                        new RawContent.FactionDef("CROWN", "C"), new RawContent.FactionDef("ASHEN", "A")),
                List.of(new RawContent.LocationDef("loc", "Loc", "VILLAGE")),
                List.of(), List.of(), List.of(), List.of(),
                List.of(new RawContent.QuestDef("q1", "Q1", "d", null, null, 0, 0, false, List.of()),
                        new RawContent.QuestDef("q2", "Q2", "d", null, null, 0, 0, false, List.of("q1"))),
                List.of(), endings, nodes, choices);
    }

    private StoryValidator run() {
        StoryValidator v = new StoryValidator(content());
        v.validate();
        return v;
    }

    private static boolean mentions(List<String> lines, String text) {
        return lines.stream().anyMatch(line -> line.contains(text));
    }

    // ---------------------------------------------------------------------

    @Test
    void validStoryHasNoErrors() {
        StoryValidator v = run();
        assertTrue(v.errors().isEmpty(), v.report());
    }

    @Test
    void realContentPassesWithoutErrors() {
        StoryValidator v = new StoryValidator(new JsonContentReader(new File("../content")).read());
        assertTrue(v.validate(), v.report());
    }

    @Test
    void check1UnreachableNode() {
        nodes.add(node("island", "ENDING"));
        assertTrue(mentions(run().errors(), "island is unreachable"));
    }

    @Test
    void check2DeadEnd() {
        nodes.add(node("stuck", "STORY"));
        choices.add(choice(4, "p02", "stuck"));
        assertTrue(mentions(run().errors(), "stuck (STORY) has no way out"));
    }

    @Test
    void check3CombatWithoutWinLose() {
        nodes.add(new NodeDef("fight", 0, "loc", "COMBAT", "f", "t", "enc", null, null, null));
        choices.add(choice(4, "p01", "fight"));
        assertTrue(mentions(run().errors(), "COMBAT node fight needs"));
    }

    @Test
    void check5QuickRouteTooLong() {
        nodes.removeIf(n -> n.id().equals("p02"));
        choices.removeIf(c -> c.fromNode().equals("p02") || c.toNode().equals("p02"));
        String previous = "p01";
        for (int i = 0; i < 13; i++) {
            nodes.add(node("long" + i, "STORY"));
            choices.add(choice(100 + i, previous, "long" + i));
            previous = "long" + i;
        }
        choices.add(choice(200, previous, "END_QUICK"));
        assertTrue(mentions(run().errors(), "Quick route is 14 steps"));
    }

    @Test
    void check6MissingTargetAndUnknownItem() {
        choices.add(choice(4, "p02", "nowhere", new RuleDef("GIVE_ITEM", "ghost_item", null, 1, 0)));
        StoryValidator v = run();
        assertTrue(mentions(v.errors(), "points to missing node nowhere"));
        assertTrue(mentions(v.errors(), "Unknown item 'ghost_item'"));
    }

    @Test
    void checks7And8FlagWarnings() {                            // D12
        choices.set(0, new ChoiceDef(1, "p01", "p02", "go", 0, false,
                List.of(new RuleDef("FLAG", "never_set", "=", 1, 0)),
                List.of(new RuleDef("SET_FLAG", "never_read", null, 1, 0))));
        StoryValidator v = run();
        assertTrue(mentions(v.warnings(), "never_set is read but never set"));
        assertTrue(mentions(v.warnings(), "never_read is set but never read"));
        assertTrue(v.errors().isEmpty(), "flag problems are warnings, not errors");
    }

    @Test
    void check9ReservedFlag() {                                 // D15
        choices.add(choice(4, "p01", "p02", new RuleDef("SET_FLAG", "_locked_passed", null, 9, 0)));
        assertTrue(mentions(run().errors(), "reserved flag _locked_passed"));
    }

    @Test
    void check10QuestTransitions() {                            // D14
        choices.add(choice(4, "p01", "p02", new RuleDef("COMPLETE_QUEST", "q1", null, 0, 0)));
        choices.add(choice(5, "p01", "p02", new RuleDef("START_QUEST", "q2", null, 0, 0)));
        StoryValidator v = run();
        assertTrue(mentions(v.errors(), "q1 is completed/failed"));
        assertFalse(mentions(v.errors(), "q2 is started"), "q1 IS completed somewhere, so q2 can unlock");
    }
}
