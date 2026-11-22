package com.shardbound.model;

import com.shardbound.engine.TestEngines;
import com.shardbound.progression.ProgressionRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The player's stored numbers, with maximums derived from the attributes (V2_SPEC §4.3, P-55). */
class StatsTest {

    private final ProgressionRules rules = TestEngines.content().progression();

    @Test
    void aNewCharacterIsLevelOneWithFullHpAndFp() {
        Stats s = Stats.starting(rules, 15);
        assertEquals(1, s.level());
        assertEquals(100, s.maxHp());
        assertEquals(100, s.hp());
        assertEquals(30, s.maxFp());
        assertEquals(30, s.fp());
        assertEquals(15, s.gold());
        assertEquals(0, s.shards());
    }

    @Test
    void healAndDamageStayWithinZeroAndMax() {
        Stats s = Stats.starting(rules, 0);
        assertEquals(30, s.damage(30));
        assertEquals(30, s.heal(45));
        assertEquals(100, s.hp());
        assertEquals(100, s.damage(500));
        assertFalse(s.isAlive());
    }

    @Test
    void fpIsSpentOnlyWhenThereIsEnoughAndRestoredToMax() {
        Stats s = Stats.starting(rules, 0);
        assertTrue(s.spendFp(25));
        assertFalse(s.spendFp(6));
        assertEquals(5, s.fp());
        assertEquals(25, s.restoreFp(99));
        assertEquals(30, s.fp());
    }

    @Test
    void goldAndShardsNeverGoBelowZero() {
        Stats s = Stats.starting(rules, 10);
        assertEquals(-10, s.addGold(-50));
        assertEquals(0, s.gold());
        assertEquals(35, s.addShards(35));
        assertEquals(-35, s.addShards(-99));
    }

    @Test
    void restoreAllFillsHpAndFp() {
        Stats s = Stats.starting(rules, 0);
        s.damage(60);
        s.spendFp(20);
        s.restoreAll();
        assertEquals(100, s.hp());
        assertEquals(30, s.fp());
    }
}
