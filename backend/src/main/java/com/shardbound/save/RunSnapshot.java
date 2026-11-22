package com.shardbound.save;

import com.shardbound.battle.BattleSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A game in progress (P-22): the saveable state plus what only exists while
 * playing, namely the mode, an unfinished battle, the ending just reached and
 * the last actions by idempotency key (P-56).
 */
public record RunSnapshot(
        SaveSnapshot game,
        String mode,
        @Nullable BattleSnapshot battle,
        boolean offerReload,
        int endingSeconds,
        int endingsFound,
        List<ActionEntry> recentActions) {

    public RunSnapshot {
        recentActions = recentActions == null ? List.of() : List.copyOf(recentActions);
    }

    /** One remembered action: its idempotency key, the request's hash and the run version it produced. */
    public record ActionEntry(String key, String requestHash, long runVersion) { }
}
