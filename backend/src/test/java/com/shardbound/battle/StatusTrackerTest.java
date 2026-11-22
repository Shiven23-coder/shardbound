package com.shardbound.battle;

import com.shardbound.engine.TestEngines;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Statuses tick at the start of their owner's turn (V2_SPEC §4.5). */
class StatusTrackerTest {

    private final Map<Status, StatusRule> rules = TestEngines.content().battle().statuses();

    @Test
    void poisonDealsFourPercentForFiveTurnsThenEnds() {
        StatusTracker t = new StatusTracker();
        t.apply(rules.get(Status.POISON));
        for (int turn = 0; turn < 5; turn++) {
            assertEquals(List.of(Map.entry(Status.POISON, 4)), t.tick(100, rules).damage());
        }
        assertFalse(t.has(Status.POISON));
    }

    @Test
    void statusDamageIsAtLeastOne() {
        StatusTracker t = new StatusTracker();
        t.apply(rules.get(Status.BLEED));
        assertEquals(1, t.tick(10, rules).damage().getFirst().getValue());   // 3 % of 10 rounds to 0
    }

    @Test
    void stunCostsOneTurnThenGivesTwoTurnsOfImmunity() {
        StatusTracker t = new StatusTracker();
        StatusRule stun = rules.get(Status.STUN);
        t.apply(stun);
        assertTrue(t.tick(100, rules).skipTurn());
        assertFalse(t.canTake(stun, Family.HUMAN));
        t.tick(100, rules);
        assertFalse(t.canTake(stun, Family.HUMAN), "still immune on the second turn");
        t.tick(100, rules);
        assertTrue(t.canTake(stun, Family.HUMAN));
    }

    @Test
    void frostSlowsAndAFireHitEndsIt() {
        StatusTracker t = new StatusTracker();
        t.apply(rules.get(Status.FROST));
        assertEquals(-3, t.speedDelta(rules));
        assertEquals(0.7, t.defenceFactor(rules), 1e-9);
        assertEquals(List.of(Status.FROST), t.removeBy(Element.FIRE, rules));
        assertEquals(0, t.speedDelta(rules));
    }

    @Test
    void aStoredTrackerComesBackTheSame() {
        StatusTracker t = new StatusTracker();
        t.apply(rules.get(Status.BURN));
        StatusTracker copy = new StatusTracker();
        copy.restore(t.active(), t.immunities());
        assertEquals(t.active(), copy.active());
    }
}
