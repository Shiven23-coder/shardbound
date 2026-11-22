package com.shardbound.engine;

import com.shardbound.battle.BattleEngine;
import com.shardbound.content.GameContent;
import com.shardbound.exception.SaveIncompatibleException;
import com.shardbound.model.Faction;
import com.shardbound.quest.QuestStatus;
import com.shardbound.save.SaveCodec;
import com.shardbound.save.SaveSnapshot;
import com.shardbound.world.AreaMap;
import com.shardbound.world.WorldPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Golden save files: every save format ever shipped must still load into the
 * CURRENT content. If a content change breaks one of these, it needs a save
 * upgrade, not a fixture edit.
 */
class SaveFixturesTest {

    private static GameSnapshots snapshots() {
        GameContent content = TestEngines.content();
        return new GameSnapshots(content, new BattleEngine(content.battle(), content.items()));
    }

    private static SaveSnapshot fixture(String name, int version) throws IOException {
        return SaveCodec.standard().readSave(Files.readString(Path.of("src/test/resources/saves/" + name)), version);
    }

    @Test
    void v1PrologueSaveLoadsIntoCurrentContent() throws IOException {
        GameState s = snapshots().restoreSave(fixture("v1/prologue-square.json", 1), "session", 7);
        assertEquals("p03", s.currentNode());
        assertEquals(Mode.DIALOGUE, s.mode());
        AreaMap square = TestEngines.content().world().mapOf("eldmere_square");
        assertEquals(WorldPosition.at("eldmere_square", square, square.spawn("from_lane").orElseThrow()), s.position());
        assertEquals(1, s.flag("dawn"), "the v1 square is after the storm night");
        assertEquals(s.stats().maxHp(), s.stats().hp(), "format 3 fills HP: v1's maximum was far lower (P-55)");
        assertEquals(s.stats().maxFp(), s.stats().fp());
        assertEquals(List.of("rending_cut", "crystal_pulse"), s.knownSkills());
        assertEquals("rusty_shortsword", s.inventory().equippedWeaponId());
        assertEquals(1, s.inventory().count("heartshard"));
        assertEquals(5, s.reputation(Faction.ELDMERE));
        assertEquals(10, s.affinity("corvin"));
        assertEquals(QuestStatus.ACTIVE, s.questStatus("q_strange_light"));
        assertEquals(List.of(new Step("p01", 1), new Step("p02", 3)), s.historyOldestFirst());
        assertEquals(95, s.playSeconds());
    }

    @Test
    void restoringASnapshotLosesNothing() throws IOException {
        GameSnapshots snapshots = snapshots();
        // The first restore also fills in quests the save did not mention; after that it is a fixed point.
        SaveSnapshot once = snapshots.save(snapshots.restoreSave(fixture("v1/prologue-square.json", 1), "s", 7));
        SaveSnapshot twice = snapshots.save(snapshots.restoreSave(once, "s", 7));
        assertEquals(withoutTime(once), withoutTime(twice));
    }

    /** playSeconds keeps ticking between the two restores; everything else must match exactly. */
    private static SaveSnapshot withoutTime(SaveSnapshot s) {
        return new SaveSnapshot(s.currentNode(), s.world(), s.hp(), s.fp(), s.gold(), s.shards(), s.level(),
                s.attributes(), s.skills(), s.loadout(), s.creeps(), s.inventory(), s.equipped(), s.flags(),
                s.reputation(), s.affinity(), s.quests(), s.history(), s.rngSeed(), s.demoMode(), 0);
    }

    @Test
    void unknownContentIsRefused() throws IOException {
        SaveSnapshot original = fixture("v1/prologue-square.json", 1);
        SaveSnapshot gone = new SaveSnapshot("p999", original.world(), original.hp(), original.fp(), original.gold(),
                original.shards(), original.level(), original.attributes(), original.skills(), original.loadout(),
                original.creeps(), original.inventory(), original.equipped(), original.flags(), original.reputation(),
                original.affinity(), original.quests(), original.history(), original.rngSeed(), original.demoMode(),
                original.playSeconds());
        assertThrows(SaveIncompatibleException.class, () -> snapshots().restoreSave(gone, "s", 7));
    }

    @Test
    void anUnknownAreaIsRefused() throws IOException {
        SaveSnapshot original = fixture("v1/prologue-square.json", 1);
        SaveSnapshot gone = new SaveSnapshot(original.currentNode(),
                new SaveSnapshot.WorldEntry("atlantis", null, 8, 8, "DOWN"), original.hp(), original.fp(),
                original.gold(), original.shards(), original.level(), original.attributes(), original.skills(),
                original.loadout(), original.creeps(), original.inventory(), original.equipped(), original.flags(),
                original.reputation(), original.affinity(), original.quests(), original.history(), original.rngSeed(),
                original.demoMode(), original.playSeconds());
        assertThrows(SaveIncompatibleException.class, () -> snapshots().restoreSave(gone, "s", 7));
    }
}
