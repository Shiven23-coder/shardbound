package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A battle in progress as stored in the run (P-22): enough to rebuild it
 * exactly, with the enemy rebuilt from its template.
 */
public record BattleSnapshot(String creepId, int level, @Nullable String creepRef, @Nullable String encounterId,
                             boolean canFlee, int round, int enemyHp, boolean enemyGuarding,
                             boolean enemyGuardedOnce, List<String> enemyTelegraphs,
                             Map<Status, Integer> enemyStatuses, Map<Status, Integer> enemyImmunity,
                             boolean playerGuarding, Map<Status, Integer> playerStatuses,
                             Map<Status, Integer> playerImmunity, List<String> text, List<HitLine> hits) {

    public BattleSnapshot {
        enemyTelegraphs = List.copyOf(enemyTelegraphs);
        enemyStatuses = Map.copyOf(enemyStatuses);
        enemyImmunity = Map.copyOf(enemyImmunity);
        playerStatuses = Map.copyOf(playerStatuses);
        playerImmunity = Map.copyOf(playerImmunity);
        text = List.copyOf(text);
        hits = List.copyOf(hits);
    }

    public static BattleSnapshot of(BattleState b) {
        CreepFighter enemy = b.enemy();
        PlayerFighter player = b.player();
        return new BattleSnapshot(enemy.template().id(), enemy.level(), b.creepRef(), b.encounterId(), b.canFlee(),
                b.round(), enemy.hp(), enemy.guarding(), enemy.guardedOnce(),
                enemy.pendingTelegraphs().stream().map(Skill::id).toList(),
                enemy.statuses().active(), enemy.statuses().immunities(), player.guarding(),
                player.statuses().active(), player.statuses().immunities(), b.text(), b.hits());
    }
}
