package com.shardbound.view;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A battle (V2_SPEC §8, P-57): both fighters, the lines of the last round,
 * an announced telegraphed move, the menu and the hits for the client's
 * effects. {@code outcome} is set on the view of the round that ended it
 * (WON, LOST or FLED); that view is shown once, then the game moves on.
 */
public record BattleView(FighterView enemy, FighterView player, int round, List<String> text,
                         @Nullable String telegraph, MenuView menu, List<HitView> hits, @Nullable String outcome,
                         @Nullable RewardsView rewards) {

    /** One side: {@code family} is null for the player. */
    public record FighterView(String name, int level, int hp, int maxHp, List<StatusView> statuses, String sprite,
                              @Nullable String family) { }

    /** A status with its icon shape and letter (never colour alone, §4.9) and turns left. */
    public record StatusView(String id, String name, String letter, String icon, int turns) { }

    /** The menu: whether FLEE is allowed, the four skill slots, the bag and the player's FP. */
    public record MenuView(boolean canFlee, List<SkillView> skills, List<BagItemView> bag, int fp, int maxFp) { }

    /** A loadout slot; {@code id} is null for an empty slot. {@code usable} is false without enough FP. */
    public record SkillView(int slot, @Nullable String id, @Nullable String name, @Nullable String element,
                            int fpCost, @Nullable String description, boolean usable) { }

    public record BagItemView(String id, String name, int qty, int heal, int fp) { }

    /**
     * A hit of the last round; {@code affinity} (W, N, R, I) and {@code element} are null for status
     * damage. {@code line} is the index in {@code text} of the line the arena plays it with.
     */
    public record HitView(boolean onPlayer, int amount, boolean crit, @Nullable String affinity,
                          @Nullable String status, @Nullable String element, int line) { }

    /** What a won battle gave: Shards and item names. */
    public record RewardsView(int shards, List<String> items) { }
}
