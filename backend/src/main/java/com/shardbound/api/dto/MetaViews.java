package com.shardbound.api.dto;

import java.util.List;
import java.util.Map;

/** Response bodies for profiles, saves, achievements, endings, leaderboard and the inspector. */
public final class MetaViews {

    private MetaViews() {
    }

    public record ProfileResponse(int playerId, String username) { }

    public record OkResponse(boolean ok) {
        public static final OkResponse OK = new OkResponse(true);
    }

    /** exists=false → the other fields are null. */
    public record SlotView(int slot, boolean exists, String nodeTitle, Integer chapter, Integer level,
                           Integer playSeconds, String updatedAt) { }

    public record AchievementView(String id, String title, String description, boolean hidden, boolean unlocked) { }

    public record EndingInfoView(String id, String title, boolean reached, Integer bestSeconds) { }

    public record LeaderboardView(String username, int endings, int achievements, Integer fastestEndingSeconds) { }

    public record QuestStatusView(String id, String status) { }

    public record InspectorView(String nodeId, String mode, long rngSeed, Map<String, Integer> flags,
                                Map<String, Integer> reputation, Map<String, Integer> affinity,
                                List<QuestStatusView> quests, List<String> recentSql) { }

    public record ErrorResponse(String error, String message) { }
}
