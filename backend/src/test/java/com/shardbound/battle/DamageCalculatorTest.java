package com.shardbound.battle;

import com.shardbound.engine.TestEngines;
import com.shardbound.item.Weapon;
import com.shardbound.model.Stats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The V2 damage formula (V2_SPEC §4.7) on the real content. A roll of 0.5 is ×1.0. */
class DamageCalculatorTest {

    private final BattleContent content = TestEngines.content().battle();
    private final DamageCalculator calculator = new DamageCalculator(content);
    private final Weapon rusty = (Weapon) TestEngines.content().items().get("rusty_shortsword");

    private PlayerFighter player() {
        return new PlayerFighter(Stats.starting(TestEngines.content().progression(), 0), rusty);
    }

    private CreepFighter creep(String id, int level) {
        return new CreepFighter(content.creeps().get(id), level, content.rules());
    }

    private Skill skill(String id) {
        return content.skills().get(id);
    }

    @Test
    void strikeIsTheAttackRatingThroughTheDefenceRatio() {
        CreepFighter slime = creep("mire_slime", 1);                          // DEF 8
        DamageCalculator.Hit hit = calculator.hit(player(), slime, Skill.STRIKE, false, new ScriptedRandom());
        // AR 20 (base 20 · F 1.0; attributes at 10 add nothing) · 100 / 108
        assertEquals(Math.round(20 * 100 / 108.0), hit.amount());
        assertEquals(Affinity.N, hit.affinity());
    }

    @Test
    void aWeaknessHitsHalfAgainAndAResistanceHalf() {
        CreepFighter slime = creep("mire_slime", 1);                          // Sylvan: Fire W
        CreepFighter rat = creep("storm_rat", 1);                             // Ashen: Crystal R
        DamageCalculator.Hit ember = calculator.hit(player(), slime, skill("ember"), false, new ScriptedRandom());
        DamageCalculator.Hit pulse = calculator.hit(player(), rat, skill("crystal_pulse"), false, new ScriptedRandom());
        assertEquals(Math.round(20 * 1.6 * 1.5 * 100 / 108.0), ember.amount());
        assertEquals(Affinity.W, ember.affinity());
        assertEquals(Math.round(20 * 1.6 * 0.5 * 100 / 104.0), pulse.amount());
        assertEquals(Affinity.R, pulse.affinity());
    }

    @Test
    void anImmuneFamilyTakesNothingAndNoStatus() {
        CreepFighter rat = creep("storm_rat", 1);                             // Ashen: Fire I
        DamageCalculator.Hit hit = calculator.hit(player(), rat, skill("ember"), false, new ScriptedRandom().picks(0, 0));
        assertEquals(0, hit.amount());
        assertEquals(Affinity.I, hit.affinity());
        assertNull(hit.status(), "an immune hit applies no status (P-54)");
    }

    @Test
    void aCritIsHalfAgainAndRollsComeInAFixedOrder() {
        CreepFighter slime = creep("mire_slime", 1);
        DamageCalculator.Hit plain = calculator.hit(player(), slime, Skill.STRIKE, false, new ScriptedRandom().picks(99));
        DamageCalculator.Hit crit = calculator.hit(player(), slime, Skill.STRIKE, false, new ScriptedRandom().picks(0));
        assertTrue(crit.crit());
        assertEquals(Math.round(20 * 1.5 * 100 / 108.0), crit.amount());
        assertEquals(Math.round(20 * 100 / 108.0), plain.amount());
        DamageCalculator.Hit low = calculator.hit(player(), slime, Skill.STRIKE, false, new ScriptedRandom().rolls(0.0));
        assertEquals(Math.round(20 * 0.9 * 100 / 108.0), low.amount());
    }

    @Test
    void guardHalvesAHitAndQuartersATelegraphedOne() {
        CreepFighter rat = creep("storm_rat", 2);                             // atk round(12 · 1.1) = 13
        PlayerFighter you = player();                                         // DEF 10
        you.setGuarding(true);
        double raw = 13 * 2.0 * 100 / 110.0;
        assertEquals(Math.round(raw * 0.5), calculator.hit(rat, you, skill("rat_frenzy"), false, new ScriptedRandom()).amount());
        assertEquals(Math.round(raw * 0.25), calculator.hit(rat, you, skill("rat_frenzy"), true, new ScriptedRandom()).amount());
    }

    @Test
    void frostLowersDefenceByThirtyPercent() {
        CreepFighter blade = creep("hired_blade", 1);                         // DEF 10
        blade.statuses().apply(content.status(Status.FROST));
        assertEquals(Math.round(20 * 100 / 107.0), calculator.hit(player(), blade, Skill.STRIKE, false,
                new ScriptedRandom()).amount());
    }

    @Test
    void aStatusProcsBelowItsChanceMinusTheTargetsResistance() {
        CreepFighter slime = creep("mire_slime", 1);                          // Mire Spit: Poison 45 %
        PlayerFighter you = player();                                         // resist 5 → chance 40
        assertEquals(Status.POISON, calculator.hit(slime, you, skill("mire_spit"), false,
                new ScriptedRandom().picks(99, 39)).status());
        assertNull(calculator.hit(slime, you, skill("mire_spit"), false, new ScriptedRandom().picks(99, 40)).status());
    }

    @Test
    void aFamilyImmuneToAStatusNeverTakesIt() {
        BattleContent battle = content;
        StatusRule burn = battle.status(Status.BURN);
        assertTrue(!burn.affects(Family.ASHEN) && burn.affects(Family.SYLVAN));
    }
}
