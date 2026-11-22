package com.shardbound.achievement;

import com.shardbound.engine.GameState;
import com.shardbound.engine.TestStates;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.item.Weapon;
import com.shardbound.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Observer-based achievements (spec §6.8, D23). meta = null: nothing is persisted. */
class AchievementManagerTest {

    private static final Map<String, Achievement> CATALOG = Map.of(
            "FIRST_LIGHT", new Achievement("FIRST_LIGHT", "First Light", "", false),
            "S_RANK", new Achievement("S_RANK", "S-Rank", "", false));

    private static AchievementManager manager(Set<String> already) {
        return new AchievementManager(1, already, CATALOG, null);
    }

    @Test
    void firstLightUnlocksOnHeartshardOnly() {
        GameState s = TestStates.fresh();
        AchievementManager m = manager(Set.of());
        m.onEvent(GameEvent.of(EventType.ITEM_GAINED, "x", "itemId", "healing_draught"), s);
        assertFalse(m.unlocked().contains("FIRST_LIGHT"));
        m.onEvent(GameEvent.of(EventType.ITEM_GAINED, "x", "itemId", "heartshard"), s);
        assertTrue(m.unlocked().contains("FIRST_LIGHT"));
        assertEquals(1, TestStates.pendingToasts(s), "one ACHIEVEMENT toast");
    }

    @Test
    void alreadyUnlockedIsNotUnlockedTwice() {                  // D23: seeded from the DB
        GameState s = TestStates.fresh();
        AchievementManager m = manager(Set.of("FIRST_LIGHT"));
        m.onEvent(GameEvent.of(EventType.ITEM_GAINED, "x", "itemId", "heartshard"), s);
        assertEquals(0, TestStates.pendingToasts(s));
    }

    @Test
    void sRankNeedsAnSOrBetterWeaponEquipped() {
        GameState s = TestStates.fresh();
        AchievementManager m = manager(Set.of());
        s.inventory().add(new Weapon("a", "A-blade", "", 0, Tier.A, 52, 12, null, "B", "C", null), 1);
        s.inventory().equip("a");
        m.onEvent(GameEvent.of(EventType.ITEM_EQUIPPED, "x", "itemId", "a"), s);
        assertFalse(m.unlocked().contains("S_RANK"));
        s.inventory().add(new Weapon("s", "S-halberd", "", 0, Tier.S, 60, 12, "B", null, null, null), 1);
        s.inventory().equip("s");
        m.onEvent(GameEvent.of(EventType.ITEM_EQUIPPED, "x", "itemId", "s"), s);
        assertTrue(m.unlocked().contains("S_RANK"));
    }

    @Test
    void silverTongueAfterThreeLockedChoices() {                // D15
        GameState s = TestStates.fresh();
        AchievementManager m = manager(Set.of());
        for (int i = 1; i <= 3; i++) {
            s.setFlag("_locked_passed", i);
            m.onEvent(new GameEvent(EventType.LOCKED_CHOICE_PASSED, Map.of()), s);
        }
        assertTrue(m.unlocked().contains("SILVER_TONGUE"));
    }

    @Test
    void speedrunnerAndShortTale() {
        GameState s = TestStates.fresh();
        AchievementManager m = manager(Set.of());
        m.onEvent(new GameEvent(EventType.ENDING_REACHED, Map.of("endingId", "END_QUICK", "seconds", 359)), s);
        assertTrue(m.unlocked().containsAll(Set.of("SHORT_TALE", "SPEEDRUNNER")));
    }

    @Test
    void allEndingsUnlocksOnlyWhenEveryEndingIsFound() {
        GameState s = TestStates.fresh();
        AchievementManager m = manager(Set.of());
        m.onEvent(new GameEvent(EventType.ENDING_REACHED,
                Map.of("endingId", "END_TRUE", "seconds", 900, "endingsFound", 5, "endingsTotal", 6)), s);
        assertFalse(m.unlocked().contains("ALL_ENDINGS"));
        m.onEvent(new GameEvent(EventType.ENDING_REACHED,
                Map.of("endingId", "END_EXILE", "seconds", 900, "endingsFound", 6, "endingsTotal", 6)), s);
        assertTrue(m.unlocked().contains("ALL_ENDINGS"));
    }

    @Test
    void rulesMustMatchContent() {
        Set<String> all = new HashSet<>(AchievementManager.ruleIds());
        all.add("ALL_ENDINGS");
        AchievementManager.checkRulesMatch(all);                 // no exception
        all.remove("ARMORY");
        assertThrows(ContentValidationException.class, () -> AchievementManager.checkRulesMatch(all));
    }
}
