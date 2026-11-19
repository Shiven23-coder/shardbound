package com.shardbound.engine.port;

import com.shardbound.save.RunSnapshot;

import java.util.Optional;

/**
 * Durable copy of every game in progress (P-22). The in-memory GameState is a
 * cache; this store is the source of truth, so restarts and deploys lose nothing.
 * Writes use optimistic locking on a version number.
 */
public interface RunStore {

    /** A stored run: its owner, the version to compare against, and the snapshot. */
    record StoredRun(int playerId, long version, RunSnapshot snapshot) { }

    /**
     * Creates the run for this session, or replaces it (new game or a loaded
     * slot). Returns the new version, which is always higher than any earlier
     * version of the same session.
     */
    long replace(String sessionId, int playerId, RunSnapshot snapshot);

    /**
     * Writes only if the stored version still equals {@code expectedVersion}.
     * Returns the new version.
     *
     * @throws com.shardbound.exception.RunConflictException if another writer got there first
     */
    long update(String sessionId, long expectedVersion, RunSnapshot snapshot);

    /** The run for this session, upgraded to the current save version. */
    Optional<StoredRun> find(String sessionId);
}
