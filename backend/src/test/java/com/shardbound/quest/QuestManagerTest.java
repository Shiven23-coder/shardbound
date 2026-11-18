package com.shardbound.quest;

import com.shardbound.engine.GameState;
import com.shardbound.engine.TestStates;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.item.Item;
import com.shardbound.item.Weapon;
import com.shardbound.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * State-machine tests pass now. The topoOrder tests FAIL until the student
 * implements Kahn's algorithm (CLAUDE.md rule 8).
 */
class QuestManagerTest {

    private static Quest quest(String id, String... prereqs) {
        return new Quest(id, id.toUpperCase(), "desc", null, null, 10, 5, false, List.of(prereqs));
    }

    private static QuestManager manager(Quest... quests) {
        Map<String, Quest> map = new LinkedHashMap<>();
        for (Quest q : quests) {
            map.put(q.id(), q);
        }
        Map<String, Item> items = Map.of("blade", new Weapon("blade", "Blade", "", 0, Tier.C, 8, 8, null));
        return new QuestManager(map, items);
    }

    // ---------------------------------------------------------------------
    // Kahn's algorithm (student)
    // ---------------------------------------------------------------------

    @Test
    void topoOrderPutsPrerequisitesFirst() {
        // the shard chain from world.json: one → two → three, and edge needs all three
        QuestManager qm = manager(quest("edge", "one", "two", "three"), quest("three", "two"),
                quest("two", "one"), quest("one"));
        assertEquals(List.of("one", "two", "three", "edge"), qm.topoOrder());
    }

    @Test
    void topoOrderKeepsContentOrderAmongIndependentQuests() {
        QuestManager qm = manager(quest("a"), quest("b"), quest("c", "a"), quest("d"));
        assertEquals(List.of("a", "b", "d", "c"), qm.topoOrder());
    }

    @Test
    void topoOrderDetectsACycle() {
        QuestManager qm = manager(quest("a", "c"), quest("b", "a"), quest("c", "b"), quest("free"));
        ContentValidationException e = assertThrows(ContentValidationException.class, qm::topoOrder);
        assertTrue(e.getMessage().contains("a") && e.getMessage().contains("b") && e.getMessage().contains("c"),
                "message should name the quests in the cycle: " + e.getMessage());
    }

    @Test
    void topoOrderOfNoQuestsIsEmpty() {
        assertEquals(List.of(), manager().topoOrder());
    }

    // ---------------------------------------------------------------------
    // state machine (implemented)
    // ---------------------------------------------------------------------

    @Test
    void initialStatesDependOnPrerequisites() {
        QuestManager qm = manager(quest("one"), quest("two", "one"));
        GameState s = TestStates.fresh();
        qm.initialStates(s);
        assertEquals(QuestStatus.AVAILABLE, s.questStatus("one"));
        assertEquals(QuestStatus.LOCKED, s.questStatus("two"));
    }

    @Test
    void completingUnlocksDependantsAndGrantsRewards() {
        QuestManager qm = manager(quest("one"), quest("two", "one"));
        GameState s = TestStates.fresh();
        qm.initialStates(s);
        int goldBefore = s.stats().gold();
        qm.start(s, "one", s.bus());
        qm.complete(s, "one", s.bus());
        assertEquals(QuestStatus.COMPLETED, s.questStatus("one"));
        assertEquals(QuestStatus.AVAILABLE, s.questStatus("two"));
        assertEquals(goldBefore + 5, s.stats().gold());
        assertEquals(10, s.stats().xp());
    }

    @Test
    void invalidTransitionsAreIgnoredNotThrown() {          // D14
        QuestManager qm = manager(quest("one"), quest("two", "one"));
        GameState s = TestStates.fresh();
        qm.initialStates(s);
        qm.start(s, "two", s.bus());          // LOCKED: cannot start
        qm.complete(s, "one", s.bus());       // AVAILABLE: cannot complete
        qm.fail(s, "missing", s.bus());       // unknown quest
        assertEquals(QuestStatus.LOCKED, s.questStatus("two"));
        assertEquals(QuestStatus.AVAILABLE, s.questStatus("one"));
    }

    @Test
    void failingAnActiveQuest() {
        QuestManager qm = manager(quest("one"));
        GameState s = TestStates.fresh();
        qm.initialStates(s);
        qm.start(s, "one", s.bus());
        qm.fail(s, "one", s.bus());
        assertEquals(QuestStatus.FAILED, s.questStatus("one"));
    }
}
