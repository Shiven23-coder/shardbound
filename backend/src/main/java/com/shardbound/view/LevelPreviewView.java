package com.shardbound.view;

/**
 * What a level-up would do (P-61): the levels and their cost, the Shards left,
 * whether it is affordable, and the stats before and after. Computed by the
 * server for every change on the level-up screen; nothing is applied.
 */
public record LevelPreviewView(int levelBefore, int levelAfter, int cost, int shardsAfter, boolean affordable,
                               LanternMenuView.StatsView before, LanternMenuView.StatsView after) { }
