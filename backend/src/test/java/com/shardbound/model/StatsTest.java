package com.shardbound.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsTest {

    @Test
    void startingStatsMatchSpec() {                  // spec §6.1
        Stats s = Stats.starting();
        assertEquals(40, s.hp());
        assertEquals(6, s.atk());
        assertEquals(2, s.def());
        assertEquals(5, s.spd());
        assertEquals(3, s.energy());
        assertEquals(15, s.gold());
        assertEquals(1, s.level());
    }

    @Test
    void xpIsProgressWithinTheLevelAndKeepsLeftover() {   // D29
        Stats s = Stats.starting();
        assertEquals(0, s.addXp(90));
        assertEquals(1, s.addXp(30));                 // 120 ≥ 100 → level 2, 20 left
        assertEquals(2, s.level());
        assertEquals(20, s.xp());
        assertEquals(200, s.xpToNext());
    }

    @Test
    void severalLevelsAtOnce() {
        Stats s = Stats.starting();
        assertEquals(2, s.addXp(100 + 200 + 50));     // level 1→2 (100), 2→3 (200), 50 left
        assertEquals(3, s.level());
        assertEquals(50, s.xp());
        assertEquals(50, s.maxHp());
        assertEquals(8, s.atk());
    }

    @Test
    void healClampsToMaxAndGoldNeverNegative() {
        Stats s = new Stats(30, 0, 1, 0, 10);
        assertEquals(10, s.heal(25));
        assertEquals(40, s.hp());
        assertEquals(-10, s.addGold(-50));
        assertEquals(0, s.gold());
    }

    @Test
    void energySpendAndRefill() {
        Stats s = new Stats(40, 1, 1, 0, 0);
        assertTrue(s.spendEnergy(1));
        assertFalse(s.spendEnergy(1));
        s.refillEnergy();
        assertEquals(3, s.energy());
    }
}
