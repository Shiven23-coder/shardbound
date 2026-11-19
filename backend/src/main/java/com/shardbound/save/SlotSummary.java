package com.shardbound.save;

import java.time.Instant;

/** One filled save slot as listed on the save/load screen. */
public record SlotSummary(int slot, String areaId, int level, int playSeconds, Instant updatedAt) {
}
