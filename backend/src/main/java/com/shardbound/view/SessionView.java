package com.shardbound.view;

import com.shardbound.engine.Mode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Everything the client needs to draw the current screen (V2_SPEC §8).
 * Built by engine.ViewBuilder; the client renders it as-is and never
 * computes game rules. The world is always present (it stays on screen
 * behind dialogues and battles). Nullable parts: dialogue (DIALOGUE),
 * battle (BATTLE, and once more on the view where a battle ends), ending (ENDED).
 */
public record SessionView(
        String sessionId,
        Mode mode,
        boolean demoMode,
        WorldView world,
        @Nullable DialogueView dialogue,
        HudView hud,
        InventoryView inventory,
        List<QuestView> quests,
        List<ReputationView> reputation,
        List<AffinityView> affinity,
        List<JournalView> journal,
        List<ToastView> toasts,
        @Nullable BattleView battle,
        @Nullable LanternMenuView lantern,
        @Nullable EndingView ending,
        @Nullable String objective,
        boolean offerReload) {

    /**
     * A dialogue page; {@code speaker} is the NPC whose portrait is shown. {@code message} is true when
     * the only option just closes the dialogue (no effects): the client shows it as a message box,
     * closed with E, A or a tap, without a choice button.
     */
    public record DialogueView(String nodeId, String title, @Nullable String speaker, String text,
                               List<ChoiceView> choices, boolean message) { }

    public record InventoryView(int usedSlots, int capacity, List<ItemView> items) { }

    /**
     * An item in the bag. {@code weapon} is set for weapons only (P-65); heal/fp are null for non-consumables.
     * For a flask (P-63) {@code qty} is its charges left and {@code maxQty} its charges when full.
     */
    public record ItemView(String id, String name, String type, String description, int qty, boolean equipped,
                           @Nullable WeaponView weapon, @Nullable Integer heal, @Nullable Integer fp,
                           boolean usable, @Nullable Integer maxQty) { }

    public record QuestView(String id, String title, String description, String status, boolean main) { }

    public record ReputationView(String faction, String name, int value) { }

    public record AffinityView(String npcId, String name, int value) { }

    public record JournalView(String nodeTitle, String choiceLabel) { }

    public record EndingView(String id, String title, String text, int seconds, int endingsFound, int endingsTotal) { }
}
