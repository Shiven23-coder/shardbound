package com.shardbound.progression;

import com.shardbound.engine.TestEngines;
import com.shardbound.item.Weapon;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Derived stats from attributes and curves (V2_SPEC §4.3, §4.6). */
class PlayerStatsTest {

    private final ProgressionRules rules = TestEngines.content().progression();

    @Test
    void everyAttributeAtTenIsTheSpecsStartingCharacter() {
        PlayerStats p = new PlayerStats(Attributes.starting(10), rules);
        assertEquals(1, p.level());
        assertEquals(100, p.maxHp());
        assertEquals(30, p.maxFp());
        assertEquals(10, p.defence());
        assertEquals(6, p.speed());
        assertEquals(5, p.statusResistPct());
        assertEquals(0, p.arcaneStatusPct());
        assertEquals(20, p.mindPower(), 1e-9);
        assertEquals(20, p.arcanePower(), 1e-9);
        Weapon rusty = (Weapon) TestEngines.content().items().get("rusty_shortsword");
        assertEquals(5, p.critPct(rusty));
        assertEquals(20, p.attackRating(rusty), 1e-9);
    }

    @Test
    void curvesInterpolateBetweenPointsAndStayFlatBeyondThem() {
        Curve hp = rules.vigorHp();
        assertEquals(140, hp.at(15), 1e-9);
        assertEquals(100, hp.at(1), 1e-9);
        assertEquals(600, hp.at(150), 1e-9);
    }

    @Test
    void scalingGradesAddToTheAttackRating() {
        Attributes strong = Attributes.of(Map.of(Attribute.STRENGTH, 20), 10);
        PlayerStats p = new PlayerStats(strong, rules);
        Weapon hatchet = (Weapon) TestEngines.content().items().get("woodcutter_hatchet");   // E 28, Str C
        assertEquals(28 * 1.10 + 28 * 0.6 * 0.35, p.attackRating(hatchet), 1e-9);
        assertEquals(11, p.level());
    }
}
