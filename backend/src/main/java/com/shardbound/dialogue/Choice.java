package com.shardbound.dialogue;

import com.shardbound.rules.ConditionGroup;
import com.shardbound.rules.Effect;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One option on a dialogue page (an edge of the dialogue graph). Immutable
 * content: conditions decide whether it can be taken; effects run when it is
 * taken. {@code to} is null when the choice ends the dialogue and returns the
 * player to the world.
 */
public record Choice(int id, String from, @Nullable String to, String label, int sortOrder, boolean hiddenWhenLocked,
                     ConditionGroup conditions, List<Effect> effects) {

    public Choice {
        effects = List.copyOf(effects);
    }

    public boolean endsDialogue() {
        return to == null;
    }

    /** True if passing this choice counts toward SILVER_TONGUE (D15). O(c). */
    public boolean hasRepOrAffinityCondition() {
        return conditions.containsRepOrAffinity();
    }
}
