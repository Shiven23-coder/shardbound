package com.shardbound.save;

import com.shardbound.combat.CombatSnapshot;
import org.jspecify.annotations.Nullable;

/**
 * A game in progress (P-22): the saveable state plus what only exists while
 * playing, namely the mode, an unfinished fight and the ending just reached.
 */
public record RunSnapshot(
        SaveSnapshot game,
        String mode,
        @Nullable CombatSnapshot combat,
        boolean offerReload,
        int endingSeconds,
        int endingsFound) {
}
