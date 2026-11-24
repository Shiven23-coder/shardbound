package com.shardbound.engine;

import com.shardbound.exception.WorldEventRejectedException;
import com.shardbound.item.Inventory;
import com.shardbound.item.KeyItem;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.view.ChoiceView;
import com.shardbound.view.SessionView;
import com.shardbound.view.ToastView;
import com.shardbound.view.WorldView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eldmere's chests (P-65): opened once, never empty, never lost to a full bag. */
class ChestTest {

    private static final String RUINS_CHEST = "ruins_chest";
    private static final String HATCHET = "woodcutter_hatchet";

    private final MutableClock clock = new MutableClock();
    private GameEngine engine;
    private RoutePlayer player;

    @BeforeEach
    void stormNightInTheRuins() {
        engine = TestEngines.inMemory(clock);
        player = new RoutePlayer(engine, engine.newGame(0, false, 3L), clock);
        player.choose("Help Corvin lash down his wagon");
        player.exit("to_ruins");
    }

    private GameState state() {
        return player.state();
    }

    /** Takes the Heartshard: dawn breaks in the ruins. */
    private void dawn() {
        player.interact("crater_crystal");
        player.choose("Take the crystal in your bare hands");
        player.choose("Walk on");
    }

    private static WorldView.ChestView chest(SessionView v, String id) {
        return v.world().chests().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static ToastView loot(SessionView v) {
        return v.toasts().stream().filter(t -> t.kind().equals("LOOT")).findFirst().orElseThrow();
    }

    @Test
    void theRuinsChestIsNotThereOnTheStormNight() {
        assertTrue(player.view().world().chests().isEmpty(), "chests appear from dawn");
        assertThrows(WorldEventRejectedException.class, () -> player.interact(RUINS_CHEST));
    }

    @Test
    void itOpensOnceAndGivesTheHatchetWithALootToastInItsTierColour() {
        dawn();
        assertFalse(chest(player.view(), RUINS_CHEST).open());

        SessionView v = player.interact(RUINS_CHEST);

        assertEquals(Mode.WORLD, v.mode());
        assertTrue(state().inventory().has(HATCHET));
        assertEquals(1, state().flag("chest_ruins"));
        assertTrue(chest(v, RUINS_CHEST).open());
        assertEquals("Found: Woodcutter's Hatchet", loot(v).text());
        assertEquals("Woodcutter's Hatchet", loot(v).subject(), "the client colours the name by its tier");
        assertEquals("E", loot(v).tier());
        assertTrue(loot(v).upgrade(), "AR 31 beats the Rusty Shortsword's 20");
        WorldEventRejectedException again = assertThrows(WorldEventRejectedException.class,
                () -> player.interact(RUINS_CHEST));
        assertEquals("The chest is empty.", again.getMessage());
        assertEquals(1, state().inventory().count(HATCHET));
    }

    @Test
    void aFindWeakerThanTheEquippedWeaponIsNoUpgrade() {
        dawn();
        state().inventory().add(engine.content().items().get("iron_longsword"), 1);
        state().inventory().equip("iron_longsword");

        SessionView v = player.interact(RUINS_CHEST);

        assertFalse(loot(v).upgrade(), "AR 31 does not beat the Iron Longsword's 49");
    }

    @Test
    void aWeaponAlreadyCarriedBecomesTheFallbackShards() {
        dawn();
        state().inventory().add(engine.content().items().get(HATCHET), 1);
        int before = state().stats().shards();

        SessionView v = player.interact(RUINS_CHEST);

        assertEquals(before + 172, state().stats().shards(), "one level-3 creep: round(35 · 3^1.45)");
        assertEquals("You already carry a Woodcutter's Hatchet. You take 172 Shards instead.", loot(v).text());
        assertFalse(loot(v).upgrade());
        assertEquals(1, state().flag("chest_ruins"), "never empty, and opened");
    }

    @Test
    void aFullBagLeavesTheChestClosed() {
        dawn();
        Inventory bag = state().inventory();
        for (int i = 0; bag.usedSlots() < Inventory.CAPACITY; i++) {
            bag.add(new KeyItem("pebble_" + i, "Pebble", "A pebble.", 0), 1);
        }

        SessionView v = player.interact(RUINS_CHEST);

        assertFalse(bag.has(HATCHET));
        assertEquals(0, state().flag("chest_ruins"));
        assertFalse(chest(v, RUINS_CHEST).open());
        assertTrue(v.toasts().stream().anyMatch(t -> t.text().equals("Your bag is full.")));
    }

    @Test
    void theSecretClearingHoldsTheLongbowFromDawn() {
        dawn();
        player.exit("to_lane");

        player.interact("clearing_chest");

        assertTrue(state().inventory().has("hunter_longbow"));
        assertEquals(1, state().flag("chest_clearing"));
    }

    private static List<String> labels(SessionView v) {
        return v.dialogue().choices().stream().map(ChoiceView::label).toList();
    }

    @Test
    void theAlleySpoilsOfferATonicToAPlayerWhoAlreadyCarriesTheHatchet() {
        dawn();
        player.interact(RUINS_CHEST);
        List<QuickRoute.Step> toTheSpoils = QuickRoute.STEPS.subList(
                QuickRoute.STEPS.indexOf(new QuickRoute.Exit("to_lane")),
                QuickRoute.STEPS.indexOf(new QuickRoute.Choose("Take the hatchet")));
        SessionView spoils = player.play(toTheSpoils);

        assertEquals(List.of("Take the tonic from his belt", "Leave it and go after Corvin"), labels(spoils));
        int tonics = state().inventory().count("energy_tonic");
        player.choose("Take the tonic from his belt");
        assertEquals(tonics + 1, state().inventory().count("energy_tonic"));
    }

    @Test
    void osricHintsAtThePoachersPathUntilTheClearingIsFound() {
        dawn();
        player.exit("to_lane");
        player.interact("junction_lantern");
        player.choose("Warm your hands");
        player.interact("osric");
        SessionView more = player.choose("Sit with him");
        assertTrue(labels(more).contains("Anything worth finding nearby?"));
        SessionView hint = player.choose("Anything worth finding nearby?");
        assertTrue(hint.dialogue().text().contains("behind the south trees"));
        player.choose("I should go.");
        player.choose("Leave");

        player.interact("clearing_chest");
        player.interact("osric");
        assertFalse(labels(player.view()).contains("Anything worth finding nearby?"), "found: no more hints");
    }
}
