package com.shardbound.engine;

import com.shardbound.item.Consumable;
import com.shardbound.model.Faction;
import com.shardbound.quest.QuestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Encapsulation (D30), reseeding (D7) and deep copies (D24). */
class GameStateTest {

    @Test
    void reseedMakesLiveAndSavedSequencesIdentical() {          // D7
        GameState live = TestStates.fresh(99L, false);
        live.rng().nextInt(100);                                // play a while…
        live.rng().nextInt(100);
        long savedSeed = live.reseedForSave();
        int liveNext = live.rng().nextInt(1000);
        GameState loaded = TestStates.fresh(savedSeed, false);  // what a load would rebuild
        assertEquals(liveNext, loaded.rng().nextInt(1000));
        assertEquals(savedSeed, live.rngSeed());
    }

    @Test
    void deepCopyCopiesDataButNotBusCombatOrToasts() {          // D24
        GameState s = TestStates.fresh();
        s.setFlag("met_wardens", 1);
        s.addReputation(Faction.SYLVAN, 10);
        s.setQuestStatus("q", QuestStatus.ACTIVE);
        s.inventory().add(new Consumable("potion", "Potion", "", 0, 15, 0), 2);
        s.historyStack().push(new Step("p01", 1));
        s.addToast(new Toast("INFO", "hello"));

        GameState copy = s.deepCopy();
        assertEquals(1, copy.flag("met_wardens"));
        assertEquals(10, copy.reputation(Faction.SYLVAN));
        assertEquals(QuestStatus.ACTIVE, copy.questStatus("q"));
        assertEquals(2, copy.inventory().count("potion"));
        assertEquals(List.of(new Step("p01", 1)), copy.historyOldestFirst());
        assertNull(copy.bus());
        assertNull(copy.combat());
        assertEquals(0, copy.toastQueue().size());

        copy.setFlag("met_wardens", 2);
        copy.inventory().remove("potion", 2);
        assertEquals(1, s.flag("met_wardens"), "changing the copy never changes the live state");
        assertEquals(2, s.inventory().count("potion"));
        assertNotSame(s.stats(), copy.stats());
    }

    @Test
    void historyOldestFirstReversesTheStack() {                 // S7
        GameState s = TestStates.fresh();
        s.historyStack().push(new Step("p01", 1));
        s.historyStack().push(new Step("p02", 3));
        s.historyStack().push(new Step("p03", 5));
        assertEquals(List.of("p01", "p02", "p03"), s.historyOldestFirst().stream().map(Step::nodeId).toList());
        assertEquals("p03", s.recentHistory(1).get(0).nodeId());
    }

    @Test
    void viewsAreReadOnly() {                                   // D30
        GameState s = TestStates.fresh();
        Map<String, Integer> flags = s.flagsView();
        assertThrows(UnsupportedOperationException.class, () -> flags.put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> s.reputationView().put(Faction.CROWN, 5));
    }

    @Test
    void everyFactionStartsAtZero() {
        GameState s = TestStates.fresh();
        for (Faction f : Faction.values()) {
            assertEquals(0, s.reputation(f));
        }
    }
}
