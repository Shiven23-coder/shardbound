package com.shardbound.view;

import org.jspecify.annotations.Nullable;

/**
 * A weapon as the player would hold it (P-65): {@code attack} is the attack rating the server computes for
 * this player with this weapon (V2_SPEC §4.6) and {@code critPct} includes the Dexterity bonus. The grades
 * are S..E, or null when the weapon does not scale with that attribute.
 */
public record WeaponView(String id, String name, String tier, String description, int base, int attack, int critPct,
                         @Nullable String str, @Nullable String dex, @Nullable String arc) { }
