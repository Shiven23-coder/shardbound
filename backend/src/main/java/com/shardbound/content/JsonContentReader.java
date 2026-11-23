package com.shardbound.content;

import com.shardbound.content.RawContent.AchievementDef;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.ConsumableDef;
import com.shardbound.content.RawContent.FlaskDef;
import com.shardbound.content.RawContent.EncounterDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.ObjectiveDef;
import com.shardbound.content.RawContent.FactionDef;
import com.shardbound.content.RawContent.ItemDef;
import com.shardbound.content.RawContent.DialogueDef;
import com.shardbound.content.RawContent.EntryDef;
import com.shardbound.content.RawContent.NodeDef;
import com.shardbound.content.RawContent.AffinityCues;
import com.shardbound.content.RawContent.NpcDef;
import com.shardbound.content.RawContent.QuestDef;
import com.shardbound.content.RawContent.RuleDef;
import com.shardbound.content.RawContent.WeaponDef;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.quest.QuestStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads world.json and the dialogue files (dialogue/*.json) into a {@link RawContent}.
 * {@link ContentLoader} checks the files against their JSON Schemas first; the
 * checks here are a second line of defence with readable messages.
 */
public class JsonContentReader {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ContentFiles files;
    private int nextChoiceId = 1;     // JSON has no choice ids; number them in file order

    public JsonContentReader(ContentFiles files) {
        this.files = files;
    }

    /** Reads the files of a content folder on disk (tests and tools). */
    public JsonContentReader(File contentDir) {
        this(ContentFiles.fromDirectory(contentDir.toPath()));
    }

    /**
     * Reads world.json, then every dialogue file in name order.
     * Time O(total JSON size), space O(total content).
     */
    public RawContent read() {
        JsonNode world = parse(ContentFiles.WORLD);
        List<DialogueDef> dialogues = new ArrayList<>();
        List<NodeDef> nodes = new ArrayList<>();
        List<ChoiceDef> choices = new ArrayList<>();
        for (String dialogueFile : files.dialogueNames()) {
            readDialogue(dialogueFile, dialogues, nodes, choices);
        }
        return new RawContent(
                readFactions(world), readNpcs(world), readAffinityCues(world), readItems(world),
                readEncounters(world), readQuests(world), readAchievements(world),
                readEndings(world), readObjectives(world), dialogues, nodes, choices);
    }

    // ---------------------------------------------------------------------
    // files
    // ---------------------------------------------------------------------

    private JsonNode parse(String fileName) {
        try {
            return mapper.readTree(files.text(fileName));
        } catch (JacksonException e) {
            throw new ContentValidationException("Cannot read " + fileName + ": " + e.getOriginalMessage());
        }
    }

    // ---------------------------------------------------------------------
    // world.json sections
    // ---------------------------------------------------------------------

    private List<FactionDef> readFactions(JsonNode world) {
        List<FactionDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "factions")) {
            result.add(new FactionDef(text(n, "id"), text(n, "name")));
        }
        return result;
    }

    private AffinityCues readAffinityCues(JsonNode world) {
        JsonNode cues = world.get("affinityCues");
        if (cues == null) {
            throw new ContentValidationException("world.json has no affinityCues");
        }
        return new AffinityCues(text(cues, "warmer"), text(cues, "colder"));
    }

    private List<NpcDef> readNpcs(JsonNode world) {
        List<NpcDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "npcs")) {
            String name = text(n, "name");
            String shortName = optText(n, "shortName");
            result.add(new NpcDef(text(n, "id"), name, shortName == null ? name : shortName, optText(n, "faction")));
        }
        return result;
    }

    private List<ItemDef> readItems(JsonNode world) {
        List<ItemDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "items")) {
            WeaponDef weapon = null;
            ConsumableDef consumable = null;
            JsonNode w = n.get("weapon");
            if (w != null && !w.isNull()) {
                JsonNode scaling = w.get("scaling");
                weapon = new WeaponDef(text(w, "tier"), integer(w, "base"), integer(w, "critPct"),
                        scaling == null ? null : optText(scaling, "STR"), scaling == null ? null : optText(scaling, "DEX"),
                        scaling == null ? null : optText(scaling, "ARC"), optText(w, "ability"));
            }
            JsonNode c = n.get("consumable");
            if (c != null && !c.isNull()) {
                consumable = new ConsumableDef(optInt(c, "heal", 0), optInt(c, "fp", 0));
            }
            JsonNode f = n.get("flask");
            FlaskDef flask = f == null || f.isNull() ? null : new FlaskDef(integer(f, "charges"), integer(f, "healPct"));
            result.add(new ItemDef(text(n, "id"), text(n, "name"), text(n, "type"), text(n, "description"),
                    optInt(n, "price", 0), weapon, consumable, flask));
        }
        return result;
    }

    private List<EncounterDef> readEncounters(JsonNode world) {
        List<EncounterDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "encounters")) {
            boolean canFlee = !n.has("canFlee") || n.get("canFlee").asBoolean();
            result.add(new EncounterDef(text(n, "id"), text(n, "name"), canFlee, text(n, "creep"), integer(n, "level")));
        }
        return result;
    }

    private List<QuestDef> readQuests(JsonNode world) {
        List<QuestDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "quests")) {
            List<String> prereqs = new ArrayList<>();
            for (JsonNode p : array(n, "prereqs")) {
                prereqs.add(p.asString());
            }
            result.add(new QuestDef(text(n, "id"), text(n, "title"), text(n, "description"),
                    optText(n, "giver"), optText(n, "rewardItem"), optInt(n, "rewardShards", 0),
                    optInt(n, "rewardGold", 0), n.path("isMain").asBoolean(false), prereqs));
        }
        return result;
    }

    private List<AchievementDef> readAchievements(JsonNode world) {
        List<AchievementDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "achievements")) {
            result.add(new AchievementDef(text(n, "id"), text(n, "title"), text(n, "description"),
                    n.path("hidden").asBoolean(false)));
        }
        return result;
    }

    private List<EndingDef> readEndings(JsonNode world) {
        List<EndingDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "endings")) {
            String id = text(n, "id");
            boolean viaResolver = !n.has("viaResolver") || n.get("viaResolver").asBoolean();
            result.add(new EndingDef(id, text(n, "node"), text(n, "title"), integer(n, "priority"),
                    viaResolver, readConditions(n, "ending " + id)));
        }
        return result;
    }

    private List<ObjectiveDef> readObjectives(JsonNode world) {
        List<ObjectiveDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "objectives")) {
            String id = text(n, "id");
            result.add(new ObjectiveDef(id, text(n, "text"), readConditions(n, "objective " + id)));
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // dialogue files
    // ---------------------------------------------------------------------

    private void readDialogue(String fileName, List<DialogueDef> dialogues, List<NodeDef> nodes,
                              List<ChoiceDef> choices) {
        JsonNode root = parse(fileName);
        String dialogueId = text(root, "id");
        List<EntryDef> entries = new ArrayList<>();
        for (JsonNode e : array(root, "entries")) {
            entries.add(new EntryDef(text(e, "node"), readConditions(e, "entry of dialogue " + dialogueId)));
        }
        dialogues.add(new DialogueDef(dialogueId, optText(root, "npc"), text(root, "start"), entries));
        for (JsonNode n : array(root, "nodes")) {
            String nodeId = text(n, "id");
            String type = n.has("type") ? text(n, "type") : "TEXT";
            nodes.add(new NodeDef(nodeId, dialogueId, type, text(n, "title"), optText(n, "speaker"), text(n, "text"),
                    optText(n, "encounter"), optText(n, "win"), optText(n, "lose"), optText(n, "flee")));
            int sortOrder = 0;
            for (JsonNode c : array(n, "choices")) {
                choices.add(new ChoiceDef(nextChoiceId++, nodeId, optText(c, "to"), text(c, "label"), sortOrder++,
                        c.path("hiddenWhenLocked").asBoolean(false),
                        readConditions(c, "choice '" + text(c, "label") + "' on " + nodeId),
                        readEffects(c)));
            }
        }
    }

    /** Conditions; a QUEST value is written as a status NAME and stored as its ordinal (D13). */
    private List<RuleDef> readConditions(JsonNode owner, String where) {
        List<RuleDef> result = new ArrayList<>();
        for (JsonNode n : array(owner, "conditions")) {
            String type = text(n, "type");
            int value = "QUEST".equals(type) ? questOrdinal(n.get("value"), where) : integer(n, "value");
            result.add(new RuleDef(type, text(n, "key"), text(n, "op"), value, optInt(n, "grp", 0)));
        }
        return result;
    }

    private List<RuleDef> readEffects(JsonNode owner) {
        List<RuleDef> result = new ArrayList<>();
        for (JsonNode n : array(owner, "effects")) {
            result.add(new RuleDef(text(n, "type"), text(n, "key"), null, optInt(n, "value", 0), 0));
        }
        return result;
    }

    private int questOrdinal(JsonNode value, String where) {
        if (value == null || !value.isString()) {
            throw new ContentValidationException("QUEST condition in " + where
                    + " must use a status name such as \"COMPLETED\" (D13)");
        }
        try {
            return QuestStatus.valueOf(value.asString()).ordinal();
        } catch (IllegalArgumentException e) {
            throw new ContentValidationException("Unknown quest status '" + value.asString() + "' in " + where);
        }
    }

    // ---------------------------------------------------------------------
    // small JSON helpers with readable errors
    // ---------------------------------------------------------------------

    private static Iterable<JsonNode> array(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new ContentValidationException("'" + field + "' must be an array in " + describe(parent));
        }
        return node;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asString().isBlank()) {
            throw new ContentValidationException("Missing '" + field + "' in " + describe(node));
        }
        return value.asString();
    }

    private static String optText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static int integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new ContentValidationException("Missing or non-integer '" + field + "' in " + describe(node));
        }
        return value.asInt();
    }

    private static int optInt(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asInt();
    }

    private static String describe(JsonNode node) {
        JsonNode id = node.get("id");
        if (id != null) {
            return "object '" + id.asString() + "'";
        }
        JsonNode label = node.get("label");
        return label != null ? "choice '" + label.asString() + "'" : "an object";
    }
}
