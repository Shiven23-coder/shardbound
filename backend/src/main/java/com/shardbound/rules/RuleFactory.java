package com.shardbound.rules;

import com.shardbound.content.RawContent;
import com.shardbound.content.RawContent.RuleDef;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.item.Item;
import com.shardbound.model.Faction;
import com.shardbound.model.Npc;
import com.shardbound.quest.Quest;
import com.shardbound.quest.QuestManager;
import com.shardbound.quest.QuestStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Factory pattern: turns raw condition/effect rows (type + key + op + value)
 * into Condition and Effect objects. A switch expression on the type string
 * picks the class; ids are resolved to real objects here, once, so effects
 * hold direct references and never look anything up during play.
 */
public class RuleFactory {

    private final Map<String, Item> items;
    private final Map<String, Npc> npcs;
    private final Map<String, Quest> quests;
    private final QuestManager questManager;
    private final RawContent.AffinityCues cues;

    public RuleFactory(Map<String, Item> items, Map<String, Npc> npcs, Map<String, Quest> quests,
                       QuestManager questManager, RawContent.AffinityCues cues) {
        this.items = items;
        this.npcs = npcs;
        this.quests = quests;
        this.questManager = questManager;
        this.cues = cues;
    }

    /** Builds a ConditionGroup from condition rows, honouring grp (D4). Time O(c log g). */
    public ConditionGroup group(List<RuleDef> rows) {
        ConditionGroup.Builder builder = ConditionGroup.builder();
        for (RuleDef row : rows) {
            builder.add(row.grp(), condition(row));
        }
        return builder.build();
    }

    /** Builds the effect list for a choice, in order. O(e). */
    public List<Effect> effects(List<RuleDef> rows) {
        List<Effect> result = new ArrayList<>();
        for (RuleDef row : rows) {
            result.add(effect(row));
        }
        return result;
    }

    /** One condition row → Condition. O(1). */
    public Condition condition(RuleDef row) {
        Op op = Op.fromSymbol(row.op());
        String key = row.key();
        return switch (row.type()) {
            case "FLAG" -> new FlagCondition(key, op, row.value());
            case "REP" -> new RepCondition(faction(key), op, row.value());
            case "AFFINITY" -> new AffinityCondition(key, npc(key).name(), op, row.value());
            case "ITEM" -> new ItemCondition(key, item(key).name(), op, row.value());
            case "QUEST" -> new QuestCondition(key, quest(key).title(), op, questStatus(row.value()));
            case "STAT" -> new StatCondition(stat(key), op, row.value());
            default -> throw new ContentValidationException("Unknown condition type: " + row.type());
        };
    }

    /** One effect row → Effect. O(1). */
    public Effect effect(RuleDef row) {
        String key = row.key();
        int value = row.value();
        return switch (row.type()) {
            case "SET_FLAG" -> new SetFlagEffect(key, value);
            case "REP" -> new RepEffect(faction(key), value);
            case "AFFINITY" -> new AffinityEffect(key, cue(cues.warmer(), key), cue(cues.colder(), key), value);
            case "GIVE_ITEM" -> new GiveItemEffect(item(key), Math.max(1, value));
            case "TAKE_ITEM" -> new TakeItemEffect(item(key), Math.max(1, value));
            case "START_QUEST" -> new StartQuestEffect(questManager, quest(key).id());
            case "COMPLETE_QUEST" -> new CompleteQuestEffect(questManager, quest(key).id());
            case "FAIL_QUEST" -> new FailQuestEffect(questManager, quest(key).id());
            case "HEAL" -> new HealEffect(value);
            case "GOLD" -> new GoldEffect(value);
            case "XP" -> new XpEffect(value);
            default -> throw new ContentValidationException("Unknown effect type: " + row.type());
        };
    }

    // ---- id resolution with readable errors ----

    private Faction faction(String key) {
        try {
            return Faction.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new ContentValidationException("Unknown faction: " + key);
        }
    }

    private String cue(String template, String npcKey) {
        return template.replace("{name}", npc(npcKey).shortName());
    }

    private Npc npc(String key) {
        return require(npcs.get(key), "NPC", key);
    }

    private Item item(String key) {
        return require(items.get(key), "item", key);
    }

    private Quest quest(String key) {
        return require(quests.get(key), "quest", key);
    }

    private static String stat(String key) {
        if (!StatCondition.STATS.contains(key)) {
            throw new ContentValidationException("Unknown stat '" + key + "' (allowed: level, gold, hp)");
        }
        return key;
    }

    private static QuestStatus questStatus(int ordinal) {
        QuestStatus[] all = QuestStatus.values();
        if (ordinal < 0 || ordinal >= all.length) {
            throw new ContentValidationException("Quest status ordinal out of range: " + ordinal);
        }
        return all[ordinal];
    }

    private static <T> T require(T value, String kind, String id) {
        if (value == null) {
            throw new ContentValidationException("Unknown " + kind + ": " + id);
        }
        return value;
    }
}
