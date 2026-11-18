package com.shardbound.quest;

import java.util.List;

/**
 * Immutable quest definition. {@code prereqs} are quest ids that must be
 * COMPLETED before this quest becomes AVAILABLE. giverNpc and rewardItem may be null.
 */
public record Quest(String id, String title, String description, String giverNpc,
                    String rewardItem, int rewardXp, int rewardGold, boolean isMain,
                    List<String> prereqs) {

    public Quest {
        prereqs = List.copyOf(prereqs);
    }
}
