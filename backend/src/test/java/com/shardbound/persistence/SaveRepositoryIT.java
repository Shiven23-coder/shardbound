package com.shardbound.persistence;

import com.shardbound.config.AppConfig;
import com.shardbound.config.Database;
import com.shardbound.content.JsonContentReader;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.exception.SaveFailedException;
import com.shardbound.model.Faction;
import com.shardbound.tools.ConsoleRunner;
import com.shardbound.tools.ContentImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the student-implemented save transaction (spec §4.4).
 * Needs PostgreSQL and the test.db.* keys in db.properties (S6).
 * Run with {@code .\mvnw.cmd verify}. It rebuilds the shardbound_test schema
 * from database/*.sql and imports content/*.json first.
 */
class SaveRepositoryIT {

    private static Database db;
    private static int playerId;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        AppConfig config = AppConfig.load().forTests();
        db = Database.from(config);
        try (Connection c = db.getConnection(); Statement st = c.createStatement()) {
            for (String file : new String[]{"99_reset.sql", "01_schema.sql", "02_functions_triggers.sql", "03_views_indexes.sql"}) {
                st.execute(Files.readString(Path.of("../database", file)));   // pgjdbc runs multi-statement scripts
            }
        }
        new ContentImporter(db).importAll(new JsonContentReader(new File("../content")).read());
        playerId = new PlayerRepository(db).findOrCreate("it-player");
    }

    private static GameState playedState() {
        GameEngine offline = ConsoleRunner.offlineEngine(new File("../content"));
        GameState s = offline.newGame(playerId, true, 7L);
        var view = offline.view(s);
        offline.choose(s, view.choices().get(0).id());           // help Corvin: affinity +10
        view = offline.view(s);
        offline.choose(s, view.choices().get(0).id());           // take the crystal: item + quest
        return s;
    }

    @Test
    void saveThenLoadRoundTrips() {
        GameState s = playedState();
        s.addReputation(Faction.SYLVAN, 7);
        s.reseedForSave();
        SaveRepository repo = new SaveRepository(db, false);
        repo.save(s, playerId, 1);

        SaveData d = repo.load(playerId, 1);
        assertEquals(s.currentNode(), d.currentNode());
        assertEquals(s.stats().hp(), d.hp());
        assertEquals(s.rngSeed(), d.rngSeed());
        assertTrue(d.demoMode());
        assertEquals(7, d.reputation().get("SYLVAN"));
        assertEquals(10, d.affinity().get("corvin"));
        assertEquals("ACTIVE", d.quests().get("q_strange_light"));
        assertEquals(1, d.inventory().stream().filter(SaveData.InventoryRow::equipped).count());
        assertEquals(2, d.history().size());
        assertEquals(1, d.history().get(0).stepNo());
        assertEquals("p01", d.history().get(0).nodeId(), "step 1 is the OLDEST step (S7)");
    }

    @Test
    void overwritingBumpsVersionAndAudits() throws SQLException {
        SaveRepository repo = new SaveRepository(db, false);
        GameState s = playedState();
        repo.save(s, playerId, 2);
        repo.save(s, playerId, 2);
        try (Connection c = db.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version, save_id FROM save_slot WHERE player_id = "
                     + playerId + " AND slot_no = 2")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
            int saveId = rs.getInt(2);
            try (ResultSet audit = c.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM save_audit WHERE save_id = " + saveId)) {
                audit.next();
                assertEquals(2, audit.getInt(1), "one INSERT + one UPDATE audit row (trigger audit_save)");
            }
        }
    }

    @Test
    void crashedSaveRollsBackAndKeepsThePreviousSave() {        // spec §4.4 crash simulation
        GameState first = playedState();
        new SaveRepository(db, false).save(first, playerId, 3);
        int goldBefore = new SaveRepository(db, false).load(playerId, 3).gold();

        GameState second = playedState();
        second.stats().addGold(999);
        SaveRepository crashing = new SaveRepository(db, true);
        assertThrows(SaveFailedException.class, () -> crashing.save(second, playerId, 3));

        SaveData after = new SaveRepository(db, false).load(playerId, 3);
        assertEquals(goldBefore, after.gold(), "the old save is intact");
        assertTrue(after.inventory().size() > 0, "old inventory rows survived the rollback");
    }
}
