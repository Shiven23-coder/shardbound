package com.shardbound.view;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Response bodies for profiles, saves, achievements, endings, leaderboard and the dev inspector. */
public final class MetaViews {

    private MetaViews() {
    }

    public record ProfileResponse(int playerId, String username) { }

    /**
     * One save slot. When {@code exists} is false every other field except
     * {@code slot} is null.
     */
    public record SlotView(int slot, boolean exists, @Nullable String areaId, @Nullable String areaName,
                           @Nullable Integer level, @Nullable Integer playSeconds,
                           @Nullable String updatedAt) { }

    public record AchievementView(String id, String title, String description, boolean hidden, boolean unlocked) { }

    public record EndingInfoView(String id, String title, boolean reached, @Nullable Integer bestSeconds) { }

    public record LeaderboardView(String username, int endings, int achievements,
                                  @Nullable Integer fastestEndingSeconds) { }

    public record QuestStatusView(String id, String status) { }

    /** Dev-profile debugging aid: the raw state behind the current screen. */
    public record InspectorView(String nodeId, String mode, long rngSeed, long runVersion,
                                Map<String, Integer> flags, Map<String, Integer> reputation,
                                Map<String, Integer> affinity, List<QuestStatusView> quests) { }
}
