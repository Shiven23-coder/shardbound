package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.quest.QuestManager;

/** ACTIVE → FAILED (D14). */
public record FailQuestEffect(QuestManager quests, String questId) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        quests.fail(state, questId, bus);
    }
}
