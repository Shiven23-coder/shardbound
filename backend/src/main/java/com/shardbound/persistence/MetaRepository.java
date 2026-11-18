package com.shardbound.persistence;

import com.shardbound.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Profile-level progress: achievements, endings reached, and the leaderboard view. */
public class MetaRepository {

    /** One row of the v_leaderboard view. fastestEndingSeconds is null if no ending yet. */
    public record LeaderboardRow(String username, int endings, int achievements, Integer fastestEndingSeconds) { }

    // Idempotent: unlocking twice is harmless.
    private static final String UNLOCK = """
            INSERT INTO player_achievement (player_id, achievement_id) VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """;

    // First arrival INSERTs (firing trigger all_endings); later arrivals keep the best time.
    private static final String RECORD_ENDING = """
            INSERT INTO player_ending (player_id, ending_id, best_seconds) VALUES (?, ?, ?)
            ON CONFLICT (player_id, ending_id) DO UPDATE
               SET best_seconds = LEAST(player_ending.best_seconds, EXCLUDED.best_seconds)
            """;

    private static final String UNLOCKED_FOR =
            "SELECT achievement_id FROM player_achievement WHERE player_id = ?";

    private static final String ENDINGS_FOR =
            "SELECT ending_id, best_seconds FROM player_ending WHERE player_id = ?";

    private static final String LEADERBOARD = """
            SELECT username, endings, achievements, fastest_ending_s
            FROM v_leaderboard
            ORDER BY endings DESC, achievements DESC, fastest_ending_s ASC NULLS LAST, username
            """;

    private final Database db;

    public MetaRepository(Database db) {
        this.db = db;
    }

    /** Persists an unlock; returns true if it was new. One statement. */
    public boolean unlockAchievement(int playerId, String achievementId) {
        SqlLog.record(UNLOCK);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(UNLOCK)) {
            ps.setInt(1, playerId);
            ps.setString(2, achievementId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RepositoryException("Could not save achievement " + achievementId, e);
        }
    }

    /** Upserts an ending, keeping the lowest best_seconds (spec §6.9). One statement. */
    public void recordEnding(int playerId, String endingId, int seconds) {
        SqlLog.record(RECORD_ENDING);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(RECORD_ENDING)) {
            ps.setInt(1, playerId);
            ps.setString(2, endingId);
            ps.setInt(3, seconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Could not record ending " + endingId, e);
        }
    }

    /** Achievement ids already unlocked by this player (seeds AchievementManager, D23). */
    public Set<String> unlockedFor(int playerId) {
        SqlLog.record(UNLOCKED_FOR);
        Set<String> result = new HashSet<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(UNLOCKED_FOR)) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load achievements for player " + playerId, e);
        }
        return result;
    }

    /** ending_id → best_seconds for every ending this player has reached. */
    public Map<String, Integer> endingsFor(int playerId) {
        SqlLog.record(ENDINGS_FOR);
        Map<String, Integer> result = new HashMap<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(ENDINGS_FOR)) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load endings for player " + playerId, e);
        }
        return result;
    }

    /** Rows of v_leaderboard, best first. */
    public List<LeaderboardRow> leaderboard() {
        SqlLog.record(LEADERBOARD);
        List<LeaderboardRow> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(LEADERBOARD);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int fastest = rs.getInt(4);
                Integer fastestOrNull = rs.wasNull() ? null : fastest;
                result.add(new LeaderboardRow(rs.getString(1), rs.getInt(2), rs.getInt(3), fastestOrNull));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the leaderboard", e);
        }
        return result;
    }
}
