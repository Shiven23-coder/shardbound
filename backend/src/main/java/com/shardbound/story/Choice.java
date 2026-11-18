package com.shardbound.story;

import com.shardbound.rules.ConditionGroup;
import com.shardbound.rules.Effect;

import java.util.List;

/**
 * One option on a page (an edge of the story graph). Immutable content:
 * conditions decide whether it can be taken; effects run when it is taken.
 */
public record Choice(int id, String from, String to, String label, int sortOrder, boolean hiddenWhenLocked,
                     ConditionGroup conditions, List<Effect> effects) {

    public Choice {
        effects = List.copyOf(effects);
    }

    /** True if passing this choice counts toward SILVER_TONGUE (D15). O(c). */
    public boolean hasRepOrAffinityCondition() {
        return conditions.containsRepOrAffinity();
    }
}
