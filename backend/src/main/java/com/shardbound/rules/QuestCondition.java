package com.shardbound.rules;

import com.shardbound.engine.GameState;
import com.shardbound.quest.QuestStatus;

/**
 * Compares a quest's status. Only = and != make sense for statuses; the
 * comparison uses ordinals, which match the SQL enum order (D13).
 */
public record QuestCondition(String questId, String questTitle, Op op, QuestStatus status) implements Condition {

    @Override
    public boolean test(GameState state) {
        return op.compare(state.questStatus(questId).ordinal(), status.ordinal());
    }

    @Override
    public String requirement() {
        String word = status.name().toLowerCase();
        if (op == Op.NE) {
            return "quest \"" + questTitle + "\" not " + word;
        }
        return "quest \"" + questTitle + "\" " + word;
    }
}
