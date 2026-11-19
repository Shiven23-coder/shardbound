package com.shardbound.persistence;

import com.shardbound.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The baseline migration on a real PostgreSQL 17: player data only, constraints enforced. */
class FlywayMigrationIT extends IntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PlayerRepository players;

    @Test
    void theSchemaHoldsPlayerDataOnly() {
        List<String> tables = jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name
                """).query(String.class).list();
        assertEquals(Set.of("active_run", "flyway_schema_history", "player", "player_achievement",
                "player_ending", "save_slot"), Set.copyOf(tables));
        assertEquals(1, jdbc.sql("SELECT count(*) FROM information_schema.views WHERE table_name = 'v_leaderboard'")
                .query(Integer.class).single());
    }

    @Test
    void migratingAgainDoesNothing() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void slotNumbersAreLimitedToZeroToThree() {
        int player = players.findOrCreate("flyway-slot-check");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO save_slot (player_id, slot_no, save_version, level, area_id, playtime_seconds, data)
                VALUES (:p, 4, 1, 1, 'x', 0, '{}'::jsonb)
                """).param("p", player).update());
    }

    @Test
    void aSaveDocumentMustBeAJsonObject() {
        int player = players.findOrCreate("flyway-object-check");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO save_slot (player_id, slot_no, save_version, level, area_id, playtime_seconds, data)
                VALUES (:p, 1, 1, 1, 'x', 0, '[1, 2]'::jsonb)
                """).param("p", player).update());
    }
}
