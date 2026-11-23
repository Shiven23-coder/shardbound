package com.shardbound.content;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * World and dialogue content as plain rows, exactly as written in
 * content/*.json, before any Java objects are built from it.
 * {@link JsonContentReader} produces it, {@code DialogueValidator} checks it
 * and {@link ContentAssembler} builds the immutable {@link GameContent} from it.
 */
public record RawContent(
        List<FactionDef> factions,
        List<NpcDef> npcs,
        AffinityCues affinityCues,
        List<ItemDef> items,
        List<EncounterDef> encounters,
        List<QuestDef> quests,
        List<AchievementDef> achievements,
        List<EndingDef> endings,
        List<ObjectiveDef> objectives,
        List<DialogueDef> dialogues,
        List<NodeDef> nodes,
        List<ChoiceDef> choices) {

    public RawContent {
        factions = List.copyOf(factions);
        npcs = List.copyOf(npcs);
        items = List.copyOf(items);
        encounters = List.copyOf(encounters);
        quests = List.copyOf(quests);
        achievements = List.copyOf(achievements);
        endings = List.copyOf(endings);
        objectives = List.copyOf(objectives);
        dialogues = List.copyOf(dialogues);
        nodes = List.copyOf(nodes);
        choices = List.copyOf(choices);
    }

    public record FactionDef(String id, String name) { }

    /** factionId may be null. */
    public record NpcDef(String id, String name, String shortName, @Nullable String factionId) { }

    /** Story cues for affinity changes; {@code {name}} is the NPC's short name. */
    public record AffinityCues(String warmer, String colder) { }

    /** Exactly one of weapon / consumable is non-null for those types; both null for KEY. */
    public record ItemDef(String id, String name, String type, String description, int price,
                          WeaponDef weapon, ConsumableDef consumable, FlaskDef flask) { }

    /** Scaling grades are S … E, or null where the weapon does not scale. */
    public record WeaponDef(String tier, int base, int critPct, @Nullable String strGrade, @Nullable String dexGrade,
                            @Nullable String arcGrade, @Nullable String ability) { }

    public record ConsumableDef(int heal, int fp) { }

    public record FlaskDef(int charges, int healPct) { }

    /** A dialogue fight: one enemy from creeps.json at a fixed level (P-37, P-54). */
    public record EncounterDef(String id, String name, boolean canFlee, String creepId, int level) { }

    public record QuestDef(String id, String title, String description, String giverNpc, String rewardItem,
                           int rewardShards, int rewardGold, boolean isMain, List<String> prereqs) {
        public QuestDef {
            prereqs = List.copyOf(prereqs);
        }
    }

    public record AchievementDef(String id, String title, String description, boolean hidden) { }

    public record EndingDef(String id, String nodeId, String title, int priority, boolean viaResolver,
                            List<RuleDef> conditions) {
        public EndingDef {
            conditions = List.copyOf(conditions);
        }
    }

    /** A current-objective line (P-46): the first whose conditions hold is shown. */
    public record ObjectiveDef(String id, String text, List<RuleDef> conditions) {
        public ObjectiveDef {
            conditions = List.copyOf(conditions);
        }
    }

    /** One dialogue file: its id, optional NPC, default first node and conditional first nodes. */
    public record DialogueDef(String id, @Nullable String npc, String start, List<EntryDef> entries) {
        public DialogueDef {
            entries = List.copyOf(entries);
        }
    }

    public record EntryDef(String node, List<RuleDef> conditions) {
        public EntryDef {
            conditions = List.copyOf(conditions);
        }
    }

    /** encounterId/win/lose/flee are null except on COMBAT nodes; speaker may be null. */
    public record NodeDef(String id, String dialogueId, String type, String title, @Nullable String speaker,
                          String body, @Nullable String encounterId, @Nullable String winNode,
                          @Nullable String loseNode, @Nullable String fleeNode) { }

    /**
     * One choice, numbered in file order. {@code toNode} is null when the
     * choice ends the dialogue.
     */
    public record ChoiceDef(int id, String fromNode, @Nullable String toNode, String label, int sortOrder,
                            boolean hiddenWhenLocked, List<RuleDef> conditions, List<RuleDef> effects) {
        public ChoiceDef {
            conditions = List.copyOf(conditions);
            effects = List.copyOf(effects);
        }
    }

    /**
     * One condition or effect row. For effects, {@code op} is null and
     * {@code grp} is 0. For QUEST conditions, {@code value} is already the
     * QuestStatus ordinal (D13).
     */
    public record RuleDef(String type, String key, String op, int value, int grp) { }
}
