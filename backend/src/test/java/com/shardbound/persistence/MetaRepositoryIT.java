package com.shardbound.persistence;

import com.shardbound.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Achievements, endings and the leaderboard view on a real PostgreSQL. */
class MetaRepositoryIT extends IntegrationTest {

    @Autowired
    private MetaRepository meta;

    @Autowired
    private PlayerRepository players;

    @Test
    void anAchievementIsNewOnlyOnce() {
        int player = players.findOrCreate("meta-achievement");
        assertTrue(meta.unlockAchievement(player, "FIRST_BLOOD"));
        assertFalse(meta.unlockAchievement(player, "FIRST_BLOOD"));
        assertEquals(Set.of("FIRST_BLOOD"), meta.unlockedFor(player));
    }

    @Test
    void anEndingKeepsTheBestTime() {
        int player = players.findOrCreate("meta-ending");
        meta.recordEnding(player, "END_QUICK", 500);
        meta.recordEnding(player, "END_QUICK", 300);
        meta.recordEnding(player, "END_QUICK", 400);
        assertEquals(Map.of("END_QUICK", 300), meta.endingsFor(player));
    }

    @Test
    void theLeaderboardCountsEachEndingAndAchievementOnce() {
        int player = players.findOrCreate("meta-leaderboard");
        meta.recordEnding(player, "END_QUICK", 250);
        meta.recordEnding(player, "END_TRUE", 3000);
        meta.unlockAchievement(player, "SHORT_TALE");
        meta.unlockAchievement(player, "FIRST_BLOOD");
        meta.unlockAchievement(player, "SPEEDRUNNER");

        MetaRepository.LeaderboardRow row = meta.leaderboard().stream()
                .filter(r -> r.username().equals("meta-leaderboard")).findFirst().orElseThrow();
        assertEquals(2, row.endings());
        assertEquals(3, row.achievements());
        assertEquals(250, row.fastestEndingSeconds());
    }
}
