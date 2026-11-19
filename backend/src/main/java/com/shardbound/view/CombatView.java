package com.shardbound.view;

import java.util.List;

/** The fight screen. lastHits feeds the floating damage numbers (targetIndex -1 = player). */
public record CombatView(String encounterName, int round, List<EnemyView> enemies, List<String> log,
                         boolean canFlee, int skillCost, List<UsableItem> usableItems, List<HitView> lastHits) {

    public record EnemyView(int index, String name, int hp, int maxHp, boolean defending, boolean alive) { }

    public record UsableItem(String id, String name, int qty) { }

    public record HitView(int targetIndex, int amount, boolean crit) { }
}
