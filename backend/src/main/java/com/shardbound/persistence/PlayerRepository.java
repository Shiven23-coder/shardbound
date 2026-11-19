package com.shardbound.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Player profiles (dev scaffolding until accounts arrive in V2a). */
@Repository
public class PlayerRepository {

    // DO UPDATE (not DO NOTHING) so RETURNING also yields the id of an existing row.
    private static final String FIND_OR_CREATE = """
            INSERT INTO player (username) VALUES (:username)
            ON CONFLICT (username) DO UPDATE SET username = EXCLUDED.username
            RETURNING player_id
            """;

    private static final String EXISTS = "SELECT EXISTS (SELECT 1 FROM player WHERE player_id = :id)";

    private final JdbcClient jdbc;

    public PlayerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The id of the player with this username, creating the player if needed. */
    public int findOrCreate(String username) {
        return jdbc.sql(FIND_OR_CREATE).param("username", username).query(Integer.class).single();
    }

    public boolean exists(int playerId) {
        return jdbc.sql(EXISTS).param("id", playerId).query(Boolean.class).single();
    }
}
