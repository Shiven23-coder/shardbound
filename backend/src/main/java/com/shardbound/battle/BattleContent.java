package com.shardbound.battle;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything battles read from content: the element chart, the statuses, the
 * skills, the enemies and the battle numbers. Immutable and shared by all
 * sessions (D20).
 */
public record BattleContent(ElementChart chart, Map<Status, StatusRule> statuses, Map<String, Skill> skills,
                            Map<String, CreepTemplate> creeps, BattleRules rules) {

    public BattleContent {
        statuses = new EnumMap<>(statuses);
        skills = Map.copyOf(skills);
        creeps = Map.copyOf(creeps);
    }

    public StatusRule status(Status status) {
        return statuses.get(status);
    }
}
