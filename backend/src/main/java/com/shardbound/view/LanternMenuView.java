package com.shardbound.view;

import com.shardbound.progression.Attribute;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Lantern menu (P-61), shown in LANTERN mode: the Lantern's name, the
 * player's level, Shards and attributes with the derived stats, what the next
 * level costs, and the skill loadout with every skill known.
 *
 * @param nextLevelCost Shards for one more level
 * @param attributeMax  the highest value an attribute may reach
 */
public record LanternMenuView(String lanternId, String name, int level, int shards, int nextLevelCost,
                              int attributeMax, List<AttributeView> attributes, StatsView stats,
                              List<LoadoutSlotView> loadout, List<KnownSkillView> knownSkills) {

    public LanternMenuView {
        attributes = List.copyOf(attributes);
        loadout = List.copyOf(loadout);
        knownSkills = List.copyOf(knownSkills);
    }

    /** One attribute and its value. */
    public record AttributeView(Attribute id, int value) { }

    /** The stats a level-up changes: max HP and FP, defence, attack rating with the equipped weapon, speed, crit %. */
    public record StatsView(int maxHp, int maxFp, int defence, int attack, int speed, int critPct) { }

    /** A loadout slot and the skill in it, or nulls for an empty slot (not SlotView: that is a save slot). */
    public record LoadoutSlotView(int slot, @Nullable String skillId, @Nullable String name, @Nullable String element,
                                  int fpCost) { }

    /** A skill the player knows. */
    public record KnownSkillView(String id, String name, String element, int fpCost, String description) { }
}
