package com.shardbound.view;

import com.shardbound.engine.Mode;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Everything React needs to draw the current screen (spec §9.2). Built by
 * engine.ViewBuilder; React renders it as-is and never computes game rules.
 * Nullable parts: combat (only in COMBAT), ending (only in ENDED).
 */
public record GameView(
        String sessionId,
        Mode mode,
        String region,
        String location,
        String locationName,
        int chapter,
        boolean demoMode,
        NodeInfo node,
        List<ChoiceView> choices,
        HudView hud,
        InventoryView inventory,
        List<QuestView> quests,
        List<ReputationView> reputation,
        List<AffinityView> affinity,
        List<JournalView> journal,
        List<ToastView> toasts,
        @Nullable CombatView combat,
        @Nullable EndingView ending,
        boolean offerReload) {

    public record NodeInfo(String id, String title, String text) { }

    public record InventoryView(int usedSlots, int capacity, List<ItemView> items) { }

    /** tier/attack/critPct are null for non-weapons; heal/energy null for non-consumables. */
    public record ItemView(String id, String name, String type, String description, int qty, boolean equipped,
                           @Nullable String tier, @Nullable Integer attack, @Nullable Integer critPct,
                           @Nullable Integer heal, @Nullable Integer energy,
                           boolean usable) { }

    public record QuestView(String id, String title, String description, String status, boolean main) { }

    public record ReputationView(String faction, String name, int value) { }

    public record AffinityView(String npcId, String name, int value) { }

    public record JournalView(String nodeTitle, String choiceLabel) { }

    public record EndingView(String id, String title, String text, int seconds, int endingsFound, int endingsTotal) { }
}
