package com.shardbound.engine;

import com.shardbound.engine.port.RunStore;
import com.shardbound.exception.RunConflictException;
import com.shardbound.save.SaveCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A RunStore in a HashMap that still goes through the JSON codec, so tests
 * exercise exactly what PostgreSQL would store, without a database.
 */
public class InMemoryRunStore implements RunStore {

    private record Row(int playerId, long version, String json) { }

    private final SaveCodec codec = SaveCodec.standard();
    private final Map<String, Row> rows = new HashMap<>();

    @Override
    public synchronized long replace(String sessionId, int playerId, com.shardbound.save.RunSnapshot snapshot) {
        Row old = rows.get(sessionId);
        long version = old == null ? 1 : old.version() + 1;
        rows.put(sessionId, new Row(playerId, version, codec.writeRun(snapshot)));
        return version;
    }

    @Override
    public synchronized long update(String sessionId, long expectedVersion, com.shardbound.save.RunSnapshot snapshot) {
        Row old = rows.get(sessionId);
        if (old == null || old.version() != expectedVersion) {
            throw new RunConflictException(sessionId);
        }
        rows.put(sessionId, new Row(old.playerId(), old.version() + 1, codec.writeRun(snapshot)));
        return old.version() + 1;
    }

    @Override
    public synchronized Optional<StoredRun> find(String sessionId) {
        Row row = rows.get(sessionId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new StoredRun(row.playerId(), row.version(),
                codec.readRun(row.json(), codec.currentVersion())));
    }

    /** An independent copy, as if a second server read the same database. */
    public synchronized InMemoryRunStore copy() {
        InMemoryRunStore copy = new InMemoryRunStore();
        copy.rows.putAll(rows);
        return copy;
    }
}
