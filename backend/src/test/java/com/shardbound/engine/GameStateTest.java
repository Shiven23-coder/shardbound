package com.shardbound.engine;

import com.shardbound.model.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Encapsulation (D30) and reseeding (D7). */
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
