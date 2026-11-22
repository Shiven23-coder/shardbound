package com.shardbound.content;

import com.shardbound.engine.Ending;
import com.shardbound.item.Weapon;
import com.shardbound.model.Tier;
import com.shardbound.quest.QuestStatus;
import com.shardbound.dialogue.NodeType;
import com.shardbound.dialogue.DialogueGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the REAL content/*.json and builds the in-memory objects. Catches
 * typos in content files at build time, without a database.
 */
class ContentFilesTest {

    private static RawContent raw;
    private static GameContent content;

    @BeforeAll
    static void load() {
        raw = new JsonContentReader(new File("../content")).read();
        ContentFiles files = ContentFiles.fromDirectory(Path.of("../content"));
        WorldReader world = new WorldReader(files);
        BattleContentReader battle = new BattleContentReader(files);
        content = new ContentAssembler().assemble(raw, world.readWorld(), world.readRules(), battle.readBattle(),
                battle.readProgression());
    }

    @Test
    void exactlySixEndingsAndOnlyQuickIsDirect() {
        assertEquals(6, content.endings().size());
        long direct = content.endings().stream().filter(e -> !e.viaResolver()).count();
        assertEquals(1, direct);
        Ending last = content.endings().stream().filter(Ending::viaResolver).reduce((a, b) -> b).orElseThrow();
        assertTrue(last.conditions().isEmpty(), "the last resolver ending must have no conditions");
    }

    @Test
    void endAshenUsesTwoOrGroups() {                            // D4
        Ending ashen = content.endings().stream().filter(e -> e.id().equals("END_ASHEN")).findFirst().orElseThrow();
        assertEquals(2, ashen.conditions().groupCount());
    }

    @Test
    void weaponLadderMatchesSpec() {                            // spec §6.4
        String[][] ladder = {{"rusty_shortsword", "F", "20"}, {"woodcutter_hatchet", "E", "28"},
                {"hunter_longbow", "D", "30"}, {"iron_longsword", "C", "36"}, {"thornwood_spear", "B", "44"},
                {"moonsteel_blade", "A", "52"}, {"kingsguard_halberd", "S", "60"},
                {"dragonbone_greatsword", "SS", "70"}, {"aetherion", "SSS", "82"}};
        for (String[] row : ladder) {
            Weapon w = (Weapon) content.items().get(row[0]);
            assertNotNull(w, row[0]);
            assertEquals(Tier.valueOf(row[1]), w.tier());
            assertEquals(Integer.parseInt(row[2]), w.base());
        }
    }

    @Test
    void quickRouteNodesExist() {
        DialogueGraph g = content.graph();
        for (String id : List.of("p01", "p02", "p03", "p04m", "p05m", "p06m", "p07m", "p08m", "p09m", "p10m", "END_QUICK")) {
            assertNotNull(g.node(id), id);
        }
        assertEquals(NodeType.COMBAT, g.node("p07m").type());
        assertEquals(NodeType.ENDING, g.node("END_QUICK").type());
    }

    @Test
    void lockedChoicesExistAtTheSignAndTheOffer() {
        assertTrue(content.graph().choicesFrom("forest_sign").stream().anyMatch(c -> !c.conditions().isEmpty()));
        assertTrue(content.graph().choicesFrom("p04m").stream().anyMatch(c -> c.hasRepOrAffinityCondition()));
    }

    @Test
    void dialoguesOpenOnTheirEntriesFirst() {
        var crater = content.graph().dialogue("crater_crystal");
        assertEquals("crater_empty", crater.start());
        assertEquals("p02", crater.entries().getFirst().node());
    }

    @Test
    void questConditionsAreStoredAsOrdinals() {                 // D13
        RawContent.RuleDef rule = raw.endings().get(0).conditions().get(0);
        assertEquals("QUEST", rule.type());
        assertEquals(QuestStatus.COMPLETED.ordinal(), rule.value());
    }

    @Test
    void choiceIdsAreUniqueAndSortOrderStartsAtZero() {
        long distinct = raw.choices().stream().mapToInt(RawContent.ChoiceDef::id).distinct().count();
        assertEquals(raw.choices().size(), distinct);
        assertFalse(content.graph().choicesFrom("p01").isEmpty());
        assertEquals(0, content.graph().choicesFrom("p01").get(0).sortOrder());
    }

    @Test
    void structuralGraphHasCombatAndResolverEdges() {           // D10
        var structure = content.graph().structure();
        assertTrue(structure.neighbors("p07m").containsAll(List.of("p08m", "p07l")));
        assertEquals(5, structure.neighbors("c5_resolver").size());
    }
}
