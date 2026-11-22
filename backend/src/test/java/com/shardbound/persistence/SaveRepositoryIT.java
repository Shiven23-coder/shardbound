package com.shardbound.persistence;

import com.shardbound.save.SaveCodec;
import com.shardbound.save.SaveSnapshot;
import com.shardbound.save.SaveUpgrade;
import com.shardbound.save.SaveUpgrader;
import com.shardbound.save.SlotSummary;
import com.shardbound.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JSONB save slots on a real PostgreSQL (P-05). */
class SaveRepositoryIT extends IntegrationTest {

    @Autowired
    private SaveRepository saves;

    @Autowired
    private PlayerRepository players;

    @Autowired
    private JdbcClient jdbc;

    private static SaveSnapshot fixture() throws IOException {
        return SaveCodec.standard().readSave(Files.readString(Path.of("src/test/resources/saves/v1/prologue-square.json")), 1);
    }

    @Test
    void aSaveRoundTripsThroughJsonb() throws IOException {
        int player = players.findOrCreate("save-roundtrip");
        SaveSnapshot snapshot = fixture();
        saves.save(player, 1, snapshot, "eldmere_square");
        assertEquals(snapshot, saves.load(player, 1).orElseThrow());
        assertTrue(saves.load(player, 2).isEmpty());
    }

    @Test
    void overwritingKeepsOneRowAndUpdatesTheSummary() throws IOException {
        int player = players.findOrCreate("save-overwrite");
        SaveSnapshot first = fixture();
        saves.save(player, 2, first, "eldmere_square");
        SaveSnapshot later = new SaveSnapshot("p03", first.world(), 40, 3, 2, 10, 50, first.inventory(),
                first.equipped(), first.flags(), first.reputation(), first.affinity(), first.quests(), first.history(),
                99L, false, 600);
        saves.save(player, 2, later, "eldmere_mill");

        List<SlotSummary> slots = saves.list(player);
        assertEquals(1, slots.size());
        SlotSummary slot = slots.get(0);
        assertEquals(2, slot.slot());
        assertEquals("eldmere_mill", slot.areaId());
        assertEquals(2, slot.level());
        assertEquals(600, slot.playSeconds());
        assertEquals(later, saves.load(player, 2).orElseThrow());
    }

    @Test
    void deletingReportsWhetherThereWasASave() throws IOException {
        int player = players.findOrCreate("save-delete");
        saves.save(player, 3, fixture(), "eldmere_square");
        assertTrue(saves.delete(player, 3));
        assertFalse(saves.delete(player, 3));
        assertTrue(saves.list(player).isEmpty());
    }

    @Test
    void anOldSaveIsUpgradedOnLoadAndWrittenBackOnce() throws IOException {
        int player = players.findOrCreate("save-upgrade");
        saves.save(player, 1, fixture(), "eldmere_square");          // stored in the current format (2)

        SaveUpgrade addGold = new SaveUpgrade() {
            public int fromVersion() { return 2; }
            public ObjectNode apply(ObjectNode d) { d.put("gold", d.get("gold").asInt() + 100); return d; }
        };
        SaveRepository newerServer = new SaveRepository(jdbc,
                new SaveCodec(new SaveUpgrader(3, List.of(addGold))));

        assertEquals(112, newerServer.load(player, 1).orElseThrow().gold());
        assertEquals(3, jdbc.sql("SELECT save_version FROM save_slot WHERE player_id = :p AND slot_no = 1")
                .param("p", player).query(Integer.class).single());
        assertEquals(112, newerServer.load(player, 1).orElseThrow().gold(), "upgraded once, not on every load");
    }
}
