package com.shardbound.content;

import java.util.List;

/**
 * Story content as plain rows, exactly as stored in the database or written
 * in content/*.json, before any Java objects are built from it.
 *
 * <p>Both loading paths produce this shape: {@link JsonContentReader} (files)
 * and {@code persistence.ContentRepository} (database). {@link ContentAssembler}
 * then builds the immutable {@link GameContent}, and {@code StoryValidator}
 * checks it. Keeping one shape means the validator and the importer never
 * care where the content came from.
 */
public record RawContent(
        List<FactionDef> factions,
        List<LocationDef> locations,
        List<NpcDef> npcs,
        List<ItemDef> items,
        List<EnemyDef> enemies,
        List<EncounterDef> encounters,
        List<QuestDef> quests,
        List<AchievementDef> achievements,
        List<EndingDef> endings,
        List<NodeDef> nodes,
        List<ChoiceDef> choices) {

    public RawContent {
        factions = List.copyOf(factions);
        locations = List.copyOf(locations);
        npcs = List.copyOf(npcs);
        items = List.copyOf(items);
        enemies = List.copyOf(enemies);
        encounters = List.copyOf(encounters);
        quests = List.copyOf(quests);
        achievements = List.copyOf(achievements);
        endings = List.copyOf(endings);
        nodes = List.copyOf(nodes);
        choices = List.copyOf(choices);
    }

    public record FactionDef(String id, String name) { }

    public record LocationDef(String id, String name, String region) { }

    /** factionId and locationId may be null. */
    public record NpcDef(String id, String name, String factionId, String locationId) { }

    /** Exactly one of weapon / consumable is non-null for those types; both null for KEY. */
    public record ItemDef(String id, String name, String type, String description, int price,
                          WeaponDef weapon, ConsumableDef consumable) { }

    public record WeaponDef(String tier, int attack, int critPct, String ability) { }

    public record ConsumableDef(int heal, int energy) { }

    public record EnemyDef(String id, String name, int hp, int atk, int def, int spd,
                           int xpReward, int goldReward, String ai) { }

    public record EncounterDef(String id, String name, boolean canFlee, List<EncounterEnemyDef> enemies) {
        public EncounterDef {
            enemies = List.copyOf(enemies);
        }
    }

    public record EncounterEnemyDef(String enemyId, int qty) { }

    public record QuestDef(String id, String title, String description, String giverNpc, String rewardItem,
                           int rewardXp, int rewardGold, boolean isMain, List<String> prereqs) {
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

    /** encounterId/win/lose/flee are null except on COMBAT nodes. */
    public record NodeDef(String id, int chapter, String locationId, String type, String title, String body,
                          String encounterId, String winNode, String loseNode, String fleeNode) { }

    /**
     * One choice. {@code id} is the database choice_id, or a sequential number
     * when loaded straight from JSON (no database yet).
     */
    public record ChoiceDef(int id, String fromNode, String toNode, String label, int sortOrder,
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
