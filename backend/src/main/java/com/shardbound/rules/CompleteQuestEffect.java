package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.quest.QuestManager;

/** ACTIVE → COMPLETED, then rewards and newly available quests (D14). */
public record CompleteQuestEffect(QuestManager quests, String questId) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        quests.complete(state, questId, bus);
    }
}
