package com.shardbound.content;

import com.shardbound.achievement.Achievement;
import com.shardbound.achievement.AchievementManager;
import com.shardbound.combat.Encounter;
import com.shardbound.combat.EncounterSlot;
import com.shardbound.combat.EnemyTemplate;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.EncounterDef;
import com.shardbound.content.RawContent.EncounterEnemyDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.ItemDef;
import com.shardbound.content.RawContent.NodeDef;
import com.shardbound.engine.Ending;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.item.Consumable;
import com.shardbound.item.Item;
import com.shardbound.item.KeyItem;
import com.shardbound.item.Weapon;
import com.shardbound.model.Faction;
import com.shardbound.model.Location;
import com.shardbound.model.Npc;
import com.shardbound.model.Tier;
import com.shardbound.quest.Quest;
import com.shardbound.quest.QuestManager;
import com.shardbound.rules.RuleFactory;
import com.shardbound.story.Choice;
import com.shardbound.story.NodeType;
import com.shardbound.story.StoryGraph;
import com.shardbound.story.StoryNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the immutable {@link GameContent} from {@link RawContent}.
 * Order matters: items and quests first, then the RuleFactory that needs
 * them, then choices and endings, then the graph.
 * Throws ContentValidationException with a clear message on any bad id.
 * Time O(total content), space O(total content).
 */
public class ContentAssembler {

    public GameContent assemble(RawContent raw) {
        checkFactions(raw);
        Map<String, Location> locations = buildLocations(raw);
        Map<String, Npc> npcs = buildNpcs(raw);
        Map<String, Item> items = buildItems(raw);
        Map<String, EnemyTemplate> enemies = buildEnemies(raw);
        Map<String, Encounter> encounters = buildEncounters(raw, enemies);
        Map<String, Quest> quests = buildQuests(raw);
        Map<String, Achievement> achievements = buildAchievements(raw);

        QuestManager questManager = new QuestManager(quests, items);
        RuleFactory rules = new RuleFactory(items, npcs, quests, questManager);

        Map<String, StoryNode> nodes = buildNodes(raw, locations);
        List<Choice> choices = buildChoices(raw, rules, nodes);
        List<Ending> endings = buildEndings(raw, rules, nodes);
        StoryGraph graph = new StoryGraph(nodes, choices, resolverNodes(endings));

        return new GameContent(freeze(locations), freeze(npcs), freeze(items), freeze(enemies),
                freeze(encounters), freeze(achievements), List.copyOf(endings), questManager, graph);
    }

    // ---------------------------------------------------------------------

    /** The faction table must hold exactly the four Faction enum constants. O(f). */
    private void checkFactions(RawContent raw) {
        Set<String> inContent = new HashSet<>();
        for (RawContent.FactionDef f : raw.factions()) {
            inContent.add(f.id());
        }
        Set<String> inJava = new HashSet<>();
        for (Faction f : Faction.values()) {
            inJava.add(f.name());
        }
        if (!inContent.equals(inJava)) {
            throw new ContentValidationException("Factions in content " + inContent
                    + " must match the Faction enum " + inJava);
        }
    }

    private Map<String, Location> buildLocations(RawContent raw) {
        Map<String, Location> result = new LinkedHashMap<>();
        for (RawContent.LocationDef l : raw.locations()) {
            result.put(l.id(), new Location(l.id(), l.name(), l.region()));
        }
        return result;
    }

    private Map<String, Npc> buildNpcs(RawContent raw) {
        Map<String, Npc> result = new LinkedHashMap<>();
        for (RawContent.NpcDef n : raw.npcs()) {
            Faction faction = n.factionId() == null ? null : Faction.valueOf(n.factionId());
            result.put(n.id(), new Npc(n.id(), n.name(), faction, n.locationId()));
        }
        return result;
    }

    private Map<String, Item> buildItems(RawContent raw) {
        Map<String, Item> result = new LinkedHashMap<>();
        for (ItemDef d : raw.items()) {
            result.put(d.id(), buildItem(d));
        }
        return result;
    }

    private Item buildItem(ItemDef d) {
        switch (d.type()) {
            case "WEAPON" -> {
                if (d.weapon() == null) {
                    throw new ContentValidationException("Weapon " + d.id() + " has no weapon stats");
                }
                return new Weapon(d.id(), d.name(), d.description(), d.price(), Tier.valueOf(d.weapon().tier()),
                        d.weapon().attack(), d.weapon().critPct(), d.weapon().ability());
            }
            case "CONSUMABLE" -> {
                if (d.consumable() == null) {
                    throw new ContentValidationException("Consumable " + d.id() + " has no heal/energy values");
                }
                return new Consumable(d.id(), d.name(), d.description(), d.price(),
                        d.consumable().heal(), d.consumable().energy());
            }
            case "KEY" -> {
                return new KeyItem(d.id(), d.name(), d.description(), d.price());
            }
            default -> throw new ContentValidationException("Unknown item type " + d.type() + " for " + d.id());
        }
    }

    private Map<String, EnemyTemplate> buildEnemies(RawContent raw) {
        Map<String, EnemyTemplate> result = new LinkedHashMap<>();
        for (RawContent.EnemyDef e : raw.enemies()) {
            result.put(e.id(), new EnemyTemplate(e.id(), e.name(), e.hp(), e.atk(), e.def(), e.spd(),
                    e.xpReward(), e.goldReward(), e.ai()));
        }
        return result;
    }

    private Map<String, Encounter> buildEncounters(RawContent raw, Map<String, EnemyTemplate> enemies) {
        Map<String, Encounter> result = new LinkedHashMap<>();
        for (EncounterDef e : raw.encounters()) {
            List<EncounterSlot> slots = new ArrayList<>();
            for (EncounterEnemyDef slot : e.enemies()) {
                if (!enemies.containsKey(slot.enemyId())) {
                    throw new ContentValidationException("Encounter " + e.id() + " uses unknown enemy " + slot.enemyId());
                }
                slots.add(new EncounterSlot(slot.enemyId(), slot.qty()));
            }
            result.put(e.id(), new Encounter(e.id(), e.name(), e.canFlee(), slots));
        }
        return result;
    }

    private Map<String, Quest> buildQuests(RawContent raw) {
        Map<String, Quest> result = new LinkedHashMap<>();
        for (RawContent.QuestDef q : raw.quests()) {
            result.put(q.id(), new Quest(q.id(), q.title(), q.description(), q.giverNpc(), q.rewardItem(),
                    q.rewardXp(), q.rewardGold(), q.isMain(), q.prereqs()));
        }
        for (Quest quest : result.values()) {
            for (String prereq : quest.prereqs()) {
                if (!result.containsKey(prereq)) {
                    throw new ContentValidationException("Quest " + quest.id() + " requires unknown quest " + prereq);
                }
            }
        }
        return result;
    }

    private Map<String, Achievement> buildAchievements(RawContent raw) {
        Map<String, Achievement> result = new LinkedHashMap<>();
        for (RawContent.AchievementDef a : raw.achievements()) {
            result.put(a.id(), new Achievement(a.id(), a.title(), a.description(), a.hidden()));
        }
        AchievementManager.checkRulesMatch(result.keySet());
        return result;
    }

    private Map<String, StoryNode> buildNodes(RawContent raw, Map<String, Location> locations) {
        Map<String, StoryNode> result = new LinkedHashMap<>();
        for (NodeDef n : raw.nodes()) {
            if (!locations.containsKey(n.locationId())) {
                throw new ContentValidationException("Node " + n.id() + " uses unknown location " + n.locationId());
            }
            result.put(n.id(), new StoryNode(n.id(), n.chapter(), n.locationId(), NodeType.valueOf(n.type()),
                    n.title(), n.body(), n.encounterId(), n.winNode(), n.loseNode(), n.fleeNode()));
        }
        return result;
    }

    private List<Choice> buildChoices(RawContent raw, RuleFactory rules, Map<String, StoryNode> nodes) {
        List<Choice> result = new ArrayList<>();
        for (ChoiceDef c : raw.choices()) {
            if (!nodes.containsKey(c.toNode())) {
                throw new ContentValidationException("Choice '" + c.label() + "' on " + c.fromNode()
                        + " points to missing node " + c.toNode());
            }
            result.add(new Choice(c.id(), c.fromNode(), c.toNode(), c.label(), c.sortOrder(), c.hiddenWhenLocked(),
                    rules.group(c.conditions()), rules.effects(c.effects())));
        }
        return result;
    }

    private List<Ending> buildEndings(RawContent raw, RuleFactory rules, Map<String, StoryNode> nodes) {
        List<Ending> result = new ArrayList<>();
        for (EndingDef e : raw.endings()) {
            if (!nodes.containsKey(e.nodeId())) {
                throw new ContentValidationException("Ending " + e.id() + " points to missing node " + e.nodeId());
            }
            result.add(new Ending(e.id(), e.nodeId(), e.title(), e.priority(), e.viaResolver(),
                    rules.group(e.conditions())));
        }
        result.sort(Comparator.comparingInt(Ending::priority));
        return result;
    }

    private static List<String> resolverNodes(List<Ending> endings) {
        List<String> result = new ArrayList<>();
        for (Ending ending : endings) {
            if (ending.viaResolver()) {
                result.add(ending.nodeId());
            }
        }
        return result;
    }

    private static <K, V> Map<K, V> freeze(Map<K, V> map) {
        return Collections.unmodifiableMap(map);
    }
}
