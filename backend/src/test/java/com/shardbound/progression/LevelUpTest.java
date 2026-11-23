package com.shardbound.progression;

import com.shardbound.engine.TestEngines;
import com.shardbound.model.Stats;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Buying levels at a Lantern (V2_SPEC §4.3, P-61). */
class LevelUpTest {

    private final ProgressionRules rules = TestEngines.content().progression();
    private final LevelUp levelUp = new LevelUp(rules);
    private final Attributes start = Attributes.starting(rules.attributeStart());

    @Test
    void costsFollowTheSpecsCurve() {
        assertEquals(100, levelUp.cost(1));
        assertEquals(283, levelUp.cost(2));
        assertEquals(1118, levelUp.cost(5));
        assertEquals(3162, levelUp.cost(10));
        assertEquals(8944, levelUp.cost(20));
        assertEquals(16432, levelUp.cost(30));
    }

    @Test
    void severalLevelsAtOnceCostTheSumOfTheirSteps() {
        LevelUp.Plan plan = levelUp.plan(start, Map.of(Attribute.VIGOR, 2, Attribute.STRENGTH, 1), 903);
        assertEquals(3, plan.levels());
        assertEquals(100 + 283 + 520, plan.cost());
        assertEquals(4, plan.after().level());
        assertEquals(12, plan.after().get(Attribute.VIGOR));
        assertTrue(plan.affordable());
        assertFalse(levelUp.plan(start, Map.of(Attribute.VIGOR, 3), 902).affordable());
    }

    @Test
    void nothingRaisedCostsNothing() {
        LevelUp.Plan plan = levelUp.plan(start, Map.of(Attribute.MIND, 0), 0);
        assertEquals(0, plan.levels());
        assertEquals(0, plan.cost());
        assertTrue(plan.affordable());
    }

    @Test
    void attributesCanOnlyGoUpAndNotPastTheirCap() {
        assertThrows(IllegalArgumentException.class, () -> levelUp.plan(start, Map.of(Attribute.VIGOR, -1), 1000));
        int toCap = rules.levels().attributeMax() - rules.attributeStart();
        assertEquals(toCap, levelUp.plan(start, Map.of(Attribute.ARCANE, toCap), Integer.MAX_VALUE).levels());
        assertThrows(IllegalArgumentException.class,
                () -> levelUp.plan(start, Map.of(Attribute.ARCANE, toCap + 1), Integer.MAX_VALUE));
    }

    @Test
    void theLevelCapHolds() {
        int maxLevel = rules.levels().maxLevel();
        int perAttribute = (maxLevel - 1) / Attribute.values().length + 1;
        Map<Attribute, Integer> beyond = new java.util.EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) {
            beyond.put(a, perAttribute);
        }
        assertThrows(IllegalArgumentException.class, () -> levelUp.plan(start, beyond, Integer.MAX_VALUE));
    }

    @Test
    void applyingAPlanTakesItsCostAndRestsThePlayerAtTheNewMaximum() {
        Stats stats = new Stats(start, rules, 40, 5, 0, 500);
        LevelUp.Plan plan = levelUp.plan(stats.attributes(), Map.of(Attribute.VIGOR, 2), stats.shards());
        stats.applyLevelUp(plan);
        assertEquals(500 - 383, stats.shards());
        assertEquals(3, stats.level());
        assertEquals(stats.maxHp(), stats.hp());
        assertTrue(stats.maxHp() > 100, "Vigor raises max HP");
        assertEquals(stats.maxFp(), stats.fp());
    }

    @Test
    void aPlanForOtherAttributesOrBeyondTheShardsIsRefused() {
        Stats stats = new Stats(start, rules, 100, 30, 0, 99);
        assertThrows(IllegalArgumentException.class,
                () -> stats.applyLevelUp(levelUp.plan(stats.attributes(), Map.of(Attribute.VIGOR, 1), 99)));
        LevelUp.Plan stale = levelUp.plan(start.raised(Map.of(Attribute.MIND, 1)), Map.of(Attribute.VIGOR, 1), 1000);
        assertThrows(IllegalArgumentException.class, () -> new Stats(start, rules, 100, 30, 0, 1000).applyLevelUp(stale));
    }
}
