package com.shardbound.engine.port;

import java.util.Map;
import java.util.Set;

/** Per-player progress that outlives a single game: achievements and endings. */
public interface MetaStore {

    /** Records an unlock; returns true if it was new. */
    boolean unlockAchievement(int playerId, String achievementId);

    /** Records an ending, keeping the best (lowest) time. */
    void recordEnding(int playerId, String endingId, int seconds);

    Set<String> unlockedFor(int playerId);

    /** endingId → best seconds. */
    Map<String, Integer> endingsFor(int playerId);
}
