package com.shardbound.persistence;

import com.shardbound.engine.port.RunStore;
import com.shardbound.exception.RunConflictException;
import com.shardbound.save.RunSnapshot;
import com.shardbound.save.SaveCodec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * Games in progress (P-22), keyed by the SHA-256 of the client's session
 * token so a database leak does not hand out live sessions.
 *
 * <p>Writes use optimistic locking: {@link #update} only succeeds when the
 * stored version still equals the version the writer last saw.
 */
@Repository
public class ActiveRunRepository implements RunStore {

    // On a new session the version starts at 1; replacing an existing run
    // continues its version so any older in-memory copy will conflict.
    private static final String REPLACE = """
            INSERT INTO active_run (player_id, session_token_hash, version, save_version, data)
            VALUES (:player, :hash, 1, :saveVersion, CAST(:data AS jsonb))
            ON CONFLICT (session_token_hash) DO UPDATE
               SET player_id    = EXCLUDED.player_id,
                   version      = active_run.version + 1,
                   save_version = EXCLUDED.save_version,
                   data         = EXCLUDED.data,
                   updated_at   = now()
            RETURNING version
            """;

    private static final String UPDATE = """
            UPDATE active_run
               SET data = CAST(:data AS jsonb), save_version = :saveVersion,
                   version = version + 1, updated_at = now()
             WHERE session_token_hash = :hash AND version = :expected
            RETURNING version
            """;

    private static final String FIND = """
            SELECT player_id, version, save_version, data::text AS data
            FROM active_run WHERE session_token_hash = :hash
            """;

    private static final String DELETE_IDLE = "DELETE FROM active_run WHERE updated_at < :cutoff";

    private record Row(int playerId, long version, int saveVersion, String data) { }

    private final JdbcClient jdbc;
    private final SaveCodec codec;

    public ActiveRunRepository(JdbcClient jdbc, SaveCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public long replace(String sessionId, int playerId, RunSnapshot snapshot) {
        return jdbc.sql(REPLACE)
                .param("player", playerId)
                .param("hash", hash(sessionId))
                .param("saveVersion", codec.currentVersion())
                .param("data", codec.writeRun(snapshot))
                .query(Long.class).single();
    }

    @Override
    public long update(String sessionId, long expectedVersion, RunSnapshot snapshot) {
        return jdbc.sql(UPDATE)
                .param("data", codec.writeRun(snapshot))
                .param("saveVersion", codec.currentVersion())
                .param("hash", hash(sessionId))
                .param("expected", expectedVersion)
                .query(Long.class).optional()
                .orElseThrow(() -> new RunConflictException(sessionId));
    }

    @Override
    public Optional<StoredRun> find(String sessionId) {
        return jdbc.sql(FIND).param("hash", hash(sessionId))
                .query((rs, row) -> new Row(rs.getInt("player_id"), rs.getLong("version"),
                        rs.getInt("save_version"), rs.getString("data")))
                .optional()
                .map(row -> new StoredRun(row.playerId(), row.version(),
                        codec.readRun(row.data(), row.saveVersion())));
    }

    /** Deletes runs nobody has touched for {@code idle}; returns how many. */
    public int deleteIdle(Duration idle) {
        return jdbc.sql(DELETE_IDLE).param("cutoff", Timestamp.from(Instant.now().minus(idle))).update();
    }

    static byte[] hash(String sessionId) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
