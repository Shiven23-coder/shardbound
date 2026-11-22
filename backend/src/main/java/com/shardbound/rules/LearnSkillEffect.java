package com.shardbound.rules;

import com.shardbound.battle.Skill;
import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;

/** Teaches a skill and puts it in the first empty loadout slot (P-55); learning it twice does nothing. */
public record LearnSkillEffect(Skill skill) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        if (state.learnSkill(skill.id())) {
            bus.publish(GameEvent.of(EventType.SKILL_LEARNED, "Learned: " + skill.name(), "skillId", skill.id()));
        }
    }
}
