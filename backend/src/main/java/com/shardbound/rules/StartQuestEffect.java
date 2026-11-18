package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.quest.QuestManager;

/** AVAILABLE → ACTIVE. QuestManager logs and ignores invalid transitions (D14). */
public record StartQuestEffect(QuestManager quests, String questId) implements Effect {

    @Override
    public void apply(GameState state, EventBus bus) {
        quests.start(state, questId, bus);
    }
}
