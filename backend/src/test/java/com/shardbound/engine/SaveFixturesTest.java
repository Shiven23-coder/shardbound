package com.shardbound.engine;

import com.shardbound.combat.CombatEngine;
import com.shardbound.content.GameContent;
import com.shardbound.exception.SaveIncompatibleException;
import com.shardbound.model.Faction;
import com.shardbound.quest.QuestStatus;
import com.shardbound.save.SaveCodec;
import com.shardbound.save.SaveSnapshot;
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
        return new GameSnapshots(content, new CombatEngine(content.enemies(), content.encounters()));
    }

    private static SaveSnapshot fixture(String name, int version) throws IOException {
        return SaveCodec.standard().readSave(Files.readString(Path.of("src/test/resources/saves/" + name)), version);
    }

    @Test
    void v1PrologueSaveLoadsIntoCurrentContent() throws IOException {
        GameState s = snapshots().restoreSave(fixture("v1/prologue-square.json", 1), "session", 7);
        assertEquals("p03", s.currentNode());
        assertEquals(34, s.stats().hp());
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
        return new SaveSnapshot(s.currentNode(), s.hp(), s.energy(), s.level(), s.xp(), s.gold(), s.inventory(),
                s.equipped(), s.flags(), s.reputation(), s.affinity(), s.quests(), s.history(), s.rngSeed(),
                s.demoMode(), 0);
    }

    @Test
    void unknownContentIsRefused() throws IOException {
        SaveSnapshot original = fixture("v1/prologue-square.json", 1);
        SaveSnapshot gone = new SaveSnapshot("p999", original.hp(), original.energy(), original.level(),
                original.xp(), original.gold(), original.inventory(), original.equipped(), original.flags(),
                original.reputation(), original.affinity(), original.quests(), original.history(),
                original.rngSeed(), original.demoMode(), original.playSeconds());
        assertThrows(SaveIncompatibleException.class, () -> snapshots().restoreSave(gone, "s", 7));
    }
}
