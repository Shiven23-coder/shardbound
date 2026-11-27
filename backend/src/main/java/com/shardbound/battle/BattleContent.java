package com.shardbound.battle;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything battles read from content: the element chart, the statuses, the
 * skills, the enemies, the bosses, the battle numbers and the creeps' brace cue
 * (P-67). Immutable and shared by all sessions (D20).
 */
public record BattleContent(ElementChart chart, Map<Status, StatusRule> statuses, Map<String, Skill> skills,
                            Map<String, CreepTemplate> creeps, Map<String, BossTemplate> bosses, BattleRules rules,
                            BraceCue brace) {

    public BattleContent {
        statuses = new EnumMap<>(statuses);
        skills = Map.copyOf(skills);
        creeps = Map.copyOf(creeps);
        bosses = Map.copyOf(bosses);
    }

    public StatusRule status(Status status) {
        return statuses.get(status);
    }
}
