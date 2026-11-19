package com.shardbound.engine.port;

import com.shardbound.save.SaveSnapshot;
import com.shardbound.save.SlotSummary;

import java.util.List;
import java.util.Optional;

/** Save slots (0 = autosave, 1–3 manual). Implemented by the persistence layer. */
public interface SaveStore {

    /**
     * Creates or overwrites a slot. {@code areaId} is stored next to the snapshot
     * so the slot list can be shown without reading the snapshot itself.
     */
    void save(int playerId, int slot, SaveSnapshot snapshot, String areaId);

    /** The slot's snapshot, upgraded to the current save version, or empty if the slot is empty. */
    Optional<SaveSnapshot> load(int playerId, int slot);

    /** Every non-empty slot of a player, ordered by slot number. */
    List<SlotSummary> list(int playerId);

    /** Deletes a slot; returns false if it was already empty. */
    boolean delete(int playerId, int slot);
}
