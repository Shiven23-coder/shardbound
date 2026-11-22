package com.shardbound.progression;

import com.shardbound.item.Weapon;
import org.jspecify.annotations.Nullable;

/**
 * The player's battle numbers, derived from attributes and the equipped
 * weapon (V2_SPEC §4.3, §4.6). Nothing here is stored.
 */
public final class PlayerStats {

    private final Attributes attributes;
    private final ProgressionRules rules;

    public PlayerStats(Attributes attributes, ProgressionRules rules) {
        this.attributes = attributes;
        this.rules = rules;
    }

    public int level() {
        return attributes.level();
    }

    public int maxHp() {
        return (int) Math.round(rules.vigorHp().at(attributes.get(Attribute.VIGOR)));
    }

    public int maxFp() {
        return (int) Math.round(rules.mindFp().at(attributes.get(Attribute.MIND)));
    }

    public int defence() {
        return (int) Math.round(rules.enduranceDef().at(attributes.get(Attribute.ENDURANCE)));
    }

    public int statusResistPct() {
        return (int) Math.round(rules.enduranceResistPct().at(attributes.get(Attribute.ENDURANCE)));
    }

    public int arcaneStatusPct() {
        return (int) Math.round(rules.arcaneStatusPct().at(attributes.get(Attribute.ARCANE)));
    }

    /** speed = base + floor(Dexterity / dexPerPoint). */
    public int speed() {
        return rules.speedBase() + attributes.get(Attribute.DEXTERITY) / rules.speedDexPerPoint();
    }

    /** crit % = weapon crit + min(maxBonus, floor((Dexterity − start) / dexPerPct)). */
    public int critPct(@Nullable Weapon weapon) {
        int bonus = Math.max(0, attributes.get(Attribute.DEXTERITY) - rules.attributeStart()) / rules.critDexPerPct();
        return (weapon == null ? 0 : weapon.critPct()) + Math.min(rules.critMaxBonusPct(), bonus);
    }

    /** Spell power for Fire, Frost and Lightning: base + range · sat(Mind). */
    public double mindPower() {
        return rules.spellPowerBase() + rules.spellPowerRange() * sat(Attribute.MIND);
    }

    /** Spell power for Holy and Crystal: base + range · sat(Arcane). */
    public double arcanePower() {
        return rules.spellPowerBase() + rules.spellPowerRange() * sat(Attribute.ARCANE);
    }

    /**
     * Attack rating: {@code base · tier · (1 + 0.08u) + Σ base · grade(a) · (1 + 0.03u) · sat(a)} over
     * Strength, Dexterity and Arcane (V2_SPEC §4.6). Bare hands count as base 1. Upgrade levels u
     * arrive with Smiths in V6; until then every weapon is +0.
     */
    public double attackRating(@Nullable Weapon weapon) {
        if (weapon == null) {
            return 1;
        }
        int u = 0;
        double rating = weapon.base() * weapon.tier().multiplier * (1 + rules.upgradeBase() * u);
        rating += scalingPart(weapon, Attribute.STRENGTH, weapon.strGrade(), u);
        rating += scalingPart(weapon, Attribute.DEXTERITY, weapon.dexGrade(), u);
        rating += scalingPart(weapon, Attribute.ARCANE, weapon.arcGrade(), u);
        return rating;
    }

    private double scalingPart(Weapon weapon, Attribute attribute, @Nullable String grade, int u) {
        if (grade == null) {
            return 0;
        }
        return weapon.base() * rules.grade(grade) * (1 + rules.upgradeScaling() * u) * sat(attribute);
    }

    private double sat(Attribute attribute) {
        return rules.scalingSat().at(attributes.get(attribute));
    }
}
