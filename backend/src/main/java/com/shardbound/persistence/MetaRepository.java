package com.shardbound.persistence;

import com.shardbound.engine.port.MetaStore;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Achievements, endings and the leaderboard. */
@Repository
public class MetaRepository implements MetaStore {

    private static final String UNLOCK = """
            INSERT INTO player_achievement (player_id, achievement_id) VALUES (:player, :achievement)
            ON CONFLICT DO NOTHING
            """;

    // First arrival inserts; later arrivals keep the best time.
    private static final String RECORD_ENDING = """
            INSERT INTO player_ending (player_id, ending_id, best_seconds) VALUES (:player, :ending, :seconds)
            ON CONFLICT (player_id, ending_id) DO UPDATE
               SET best_seconds = LEAST(player_ending.best_seconds, EXCLUDED.best_seconds)
            """;

    private static final String UNLOCKED_FOR =
            "SELECT achievement_id FROM player_achievement WHERE player_id = :player";

    private static final String ENDINGS_FOR =
            "SELECT ending_id, best_seconds FROM player_ending WHERE player_id = :player";

    private static final String LEADERBOARD = """
            SELECT username, endings, achievements, fastest_ending_s
            FROM v_leaderboard
            ORDER BY endings DESC, achievements DESC, fastest_ending_s ASC NULLS LAST, username
            LIMIT :limit
            """;

    public static final int LEADERBOARD_SIZE = 50;

    /** One leaderboard row. */
    public record LeaderboardRow(String username, int endings, int achievements,
                                 @Nullable Integer fastestEndingSeconds) { }

    private final JdbcClient jdbc;

    public MetaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean unlockAchievement(int playerId, String achievementId) {
        return jdbc.sql(UNLOCK).param("player", playerId).param("achievement", achievementId).update() == 1;
    }

    @Override
    public void recordEnding(int playerId, String endingId, int seconds) {
        jdbc.sql(RECORD_ENDING).param("player", playerId).param("ending", endingId).param("seconds", seconds)
                .update();
    }

    @Override
    public Set<String> unlockedFor(int playerId) {
        return new HashSet<>(jdbc.sql(UNLOCKED_FOR).param("player", playerId).query(String.class).list());
    }

    @Override
    public Map<String, Integer> endingsFor(int playerId) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.sql(ENDINGS_FOR).param("player", playerId)
                .query(rs -> {
                    result.put(rs.getString("ending_id"), rs.getInt("best_seconds"));
                });
        return result;
    }

    public List<LeaderboardRow> leaderboard() {
        return jdbc.sql(LEADERBOARD).param("limit", LEADERBOARD_SIZE)
                .query((rs, row) -> new LeaderboardRow(rs.getString("username"), rs.getInt("endings"),
                        rs.getInt("achievements"), rs.getObject("fastest_ending_s", Integer.class)))
                .list();
    }
}
