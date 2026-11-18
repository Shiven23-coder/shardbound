package com.shardbound.persistence;

import com.shardbound.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Player profiles. There are no passwords: the username is the profile (S4). */
public class PlayerRepository {

    // DO UPDATE (not DO NOTHING) so RETURNING also yields the id of an EXISTING row.
    private static final String FIND_OR_CREATE = """
            INSERT INTO player (username) VALUES (?)
            ON CONFLICT (username) DO UPDATE SET username = EXCLUDED.username
            RETURNING player_id
            """;

    private static final String EXISTS = "SELECT 1 FROM player WHERE player_id = ?";

    private final Database db;

    public PlayerRepository(Database db) {
        this.db = db;
    }

    /** Creates the profile, or returns the existing one with that username. One round trip. */
    public int findOrCreate(String username) {
        SqlLog.record(FIND_OR_CREATE);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(FIND_OR_CREATE)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not create or find player " + username, e);
        }
    }

    /** True if the player id exists. */
    public boolean exists(int playerId) {
        SqlLog.record(EXISTS);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(EXISTS)) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not look up player " + playerId, e);
        }
    }
}
