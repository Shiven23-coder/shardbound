package com.shardbound.progression;

import java.util.Map;

/**
 * Attribute curves and the constants of the derived stats, from balance.json
 * {@code progression} (V2_SPEC §4.3, §4.6).
 *
 * @param gradeCoefficients weapon scaling grade (S, A, … E) → coefficient; a grade not listed scales by 0
 * @param levels            what levels cost at a Lantern, and the caps (P-61)
 */
public record ProgressionRules(int attributeStart, Curve vigorHp, Curve mindFp, Curve enduranceDef,
                               Curve enduranceResistPct, Curve scalingSat, Curve arcaneStatusPct, int speedBase,
                               int speedDexPerPoint, int critDexPerPct, int critMaxBonusPct, double spellPowerBase,
                               double spellPowerRange, Map<String, Double> gradeCoefficients, double upgradeBase,
                               double upgradeScaling, LevelRules levels) {

    /** cost(L → L+1) = round(costBase · L^costExponent); levels and attributes are capped. */
    public record LevelRules(double costBase, double costExponent, int maxLevel, int attributeMax) { }

    public ProgressionRules {
        gradeCoefficients = Map.copyOf(gradeCoefficients);
    }

    public double grade(String grade) {
        return gradeCoefficients.getOrDefault(grade, 0.0);
    }
}
