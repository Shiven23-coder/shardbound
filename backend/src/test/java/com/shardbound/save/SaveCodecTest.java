package com.shardbound.save;

import com.shardbound.exception.SaveIncompatibleException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JSON save documents and versioned upgrades (P-05). */
class SaveCodecTest {

    static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/saves/" + name));
    }

    @Test
    void theV1FixtureStillReads() throws IOException {
        SaveSnapshot s = SaveCodec.standard().readSave(fixture("v1/prologue-square.json"), 1);
        assertEquals("p03", s.currentNode());
        assertEquals(34, s.hp());
        assertEquals("rusty_shortsword", s.equipped());
        assertEquals(List.of(new SaveSnapshot.StepEntry("p01", 1), new SaveSnapshot.StepEntry("p02", 3)), s.history());
        assertEquals("ACTIVE", s.quests().get("q_strange_light"));
        assertEquals(123456789L, s.rngSeed());
    }

    @Test
    void writeThenReadGivesTheSameSnapshot() throws IOException {
        SaveCodec codec = SaveCodec.standard();
        SaveSnapshot original = codec.readSave(fixture("v1/prologue-square.json"), 1);
        assertEquals(original, codec.readSave(codec.writeSave(original), codec.currentVersion()));
    }

    @Test
    void upgradesRunInVersionOrder() throws IOException {
        // Pretend format 3 renamed "gold" to "coins" (1→2) and then back to "gold" doubled (2→3).
        SaveUpgrade renameGold = new SaveUpgrade() {
            public int fromVersion() { return 1; }
            public ObjectNode apply(ObjectNode d) { d.set("coins", d.remove("gold")); return d; }
        };
        SaveUpgrade doubleCoins = new SaveUpgrade() {
            public int fromVersion() { return 2; }
            public ObjectNode apply(ObjectNode d) { d.put("gold", d.remove("coins").asInt() * 2); return d; }
        };
        SaveCodec codec = new SaveCodec(new SaveUpgrader(3, List.of(doubleCoins, renameGold)));
        SaveSnapshot upgraded = codec.readSave(fixture("v1/prologue-square.json"), 1);
        assertEquals(24, upgraded.gold());
    }

    @Test
    void aSaveFromANewerServerIsRefused() throws IOException {
        String json = fixture("v1/prologue-square.json");
        SaveIncompatibleException e = assertThrows(SaveIncompatibleException.class,
                () -> SaveCodec.standard().readSave(json, 99));
        assertTrue(e.getMessage().contains("newer version"));
    }

    @Test
    void aGapInTheUpgradeChainIsRefused() throws IOException {
        SaveCodec codec = new SaveCodec(new SaveUpgrader(2, List.of()));
        String json = fixture("v1/prologue-square.json");
        assertThrows(SaveIncompatibleException.class, () -> codec.readSave(json, 1));
    }

    @Test
    void duplicateUpgradesAreAProgrammingError() {
        SaveUpgrade one = new SaveUpgrade() {
            public int fromVersion() { return 1; }
            public ObjectNode apply(ObjectNode d) { return d; }
        };
        assertThrows(IllegalArgumentException.class, () -> new SaveUpgrader(2, List.of(one, one)));
    }

    @Test
    void brokenDocumentsAreRefusedNotGuessedAt() {
        SaveCodec codec = SaveCodec.standard();
        assertThrows(SaveIncompatibleException.class, () -> codec.readSave("not json", 1));
        assertThrows(SaveIncompatibleException.class, () -> codec.readSave("[1, 2]", 1));
        assertThrows(SaveIncompatibleException.class, () -> codec.readSave("{\"hp\": \"lots\"}", 1));
        assertThrows(SaveIncompatibleException.class, () -> codec.readRun("{\"mode\": \"STORY\"}", 1));
    }

    @Test
    void runsRoundTripWithTheirGame() throws IOException {
        SaveCodec codec = SaveCodec.standard();
        SaveSnapshot game = codec.readSave(fixture("v1/prologue-square.json"), 1);
        RunSnapshot run = new RunSnapshot(game, "STORY", null, false, 0, 0);
        assertEquals(run, codec.readRun(codec.writeRun(run), codec.currentVersion()));
        assertEquals(Map.of("corvin", 10), codec.readRun(codec.writeRun(run), 1).game().affinity());
    }
}
