package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

/**
 * A skill or creep move from content/skills.json (V2_SPEC §4.6). The player's
 * WEAPON moves use the attack rating, MIND and ARCANE moves the spell powers;
 * a creep's moves always use its attack (P-54).
 *
 * @param telegraphLine shown one enemy turn early for a telegraphed move; {@code {name}} is the enemy's name
 */
public record Skill(String id, String name, String description, Element element, double power, int fpCost,
                    Scaling scaling, @Nullable Status status, int statusChance, boolean telegraph,
                    @Nullable String telegraphLine, Unlock unlock) {

    /** What powers a player's move. */
    public enum Scaling { WEAPON, MIND, ARCANE }

    /** How the player can get it: in the opening loadout, taught in dialogue, not yet, or never (enemies only). */
    public enum Unlock { START, DIALOGUE, LOCKED, CREEP }

    /** Strike: the plain weapon attack every fighter has (Physical, power 1.0). */
    public static final Skill STRIKE = new Skill("strike", "Strike", "A plain attack.", Element.PHYSICAL, 1.0, 0,
            Scaling.WEAPON, null, 0, false, null, Unlock.CREEP);
}
