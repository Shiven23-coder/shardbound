package com.shardbound.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardbound.content.RawContent.AchievementDef;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.ConsumableDef;
import com.shardbound.content.RawContent.EncounterDef;
import com.shardbound.content.RawContent.EncounterEnemyDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.EnemyDef;
import com.shardbound.content.RawContent.FactionDef;
import com.shardbound.content.RawContent.ItemDef;
import com.shardbound.content.RawContent.LocationDef;
import com.shardbound.content.RawContent.NodeDef;
import com.shardbound.content.RawContent.NpcDef;
import com.shardbound.content.RawContent.QuestDef;
import com.shardbound.content.RawContent.RuleDef;
import com.shardbound.content.RawContent.WeaponDef;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.quest.QuestStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads content/world.json and content/ch*.json (file I/O, spec §8) into a
 * {@link RawContent}. Shape errors (missing fields, wrong types) are reported
 * with the file and the id of the object, so content authors can find them.
 */
public class JsonContentReader {

    private final ObjectMapper mapper = new ObjectMapper();
    private final File contentDir;
    private int nextChoiceId = 1;     // JSON has no choice ids; number them in file order

    public JsonContentReader(File contentDir) {
        this.contentDir = contentDir;
    }

    /**
     * Reads world.json, then every chapter file in name order.
     * Time O(total JSON size), space O(total content).
     */
    public RawContent read() {
        JsonNode world = readFile(new File(contentDir, "world.json"));
        List<NodeDef> nodes = new ArrayList<>();
        List<ChoiceDef> choices = new ArrayList<>();
        for (File chapterFile : chapterFiles()) {
            readChapter(chapterFile, nodes, choices);
        }
        return new RawContent(
                readFactions(world), readLocations(world), readNpcs(world), readItems(world),
                readEnemies(world), readEncounters(world), readQuests(world), readAchievements(world),
                readEndings(world), nodes, choices);
    }

    // ---------------------------------------------------------------------
    // files
    // ---------------------------------------------------------------------

    private File[] chapterFiles() {
        File[] files = contentDir.listFiles((dir, name) -> name.startsWith("ch") && name.endsWith(".json"));
        if (files == null) {
            throw new ContentValidationException("Content folder not found: " + contentDir.getAbsolutePath());
        }
        Arrays.sort(files);
        return files;
    }

    private JsonNode readFile(File file) {
        try {
            return mapper.readTree(file);
        } catch (IOException e) {
            throw new ContentValidationException("Cannot read " + file.getName() + ": " + e.getMessage());
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

    private List<LocationDef> readLocations(JsonNode world) {
        List<LocationDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "locations")) {
            result.add(new LocationDef(text(n, "id"), text(n, "name"), text(n, "region")));
        }
        return result;
    }

    private List<NpcDef> readNpcs(JsonNode world) {
        List<NpcDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "npcs")) {
            result.add(new NpcDef(text(n, "id"), text(n, "name"), optText(n, "faction"), optText(n, "location")));
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
                weapon = new WeaponDef(text(w, "tier"), integer(w, "attack"), integer(w, "critPct"), optText(w, "ability"));
            }
            JsonNode c = n.get("consumable");
            if (c != null && !c.isNull()) {
                consumable = new ConsumableDef(optInt(c, "heal", 0), optInt(c, "energy", 0));
            }
            result.add(new ItemDef(text(n, "id"), text(n, "name"), text(n, "type"), text(n, "description"),
                    optInt(n, "price", 0), weapon, consumable));
        }
        return result;
    }

    private List<EnemyDef> readEnemies(JsonNode world) {
        List<EnemyDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "enemies")) {
            result.add(new EnemyDef(text(n, "id"), text(n, "name"), integer(n, "hp"), integer(n, "atk"),
                    integer(n, "def"), integer(n, "spd"), integer(n, "xpReward"), integer(n, "goldReward"),
                    text(n, "ai")));
        }
        return result;
    }

    private List<EncounterDef> readEncounters(JsonNode world) {
        List<EncounterDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "encounters")) {
            List<EncounterEnemyDef> enemies = new ArrayList<>();
            for (JsonNode e : array(n, "enemies")) {
                enemies.add(new EncounterEnemyDef(text(e, "id"), integer(e, "qty")));
            }
            boolean canFlee = !n.has("canFlee") || n.get("canFlee").asBoolean();
            result.add(new EncounterDef(text(n, "id"), text(n, "name"), canFlee, enemies));
        }
        return result;
    }

    private List<QuestDef> readQuests(JsonNode world) {
        List<QuestDef> result = new ArrayList<>();
        for (JsonNode n : array(world, "quests")) {
            List<String> prereqs = new ArrayList<>();
            for (JsonNode p : array(n, "prereqs")) {
                prereqs.add(p.asText());
            }
            result.add(new QuestDef(text(n, "id"), text(n, "title"), text(n, "description"),
                    optText(n, "giver"), optText(n, "rewardItem"), optInt(n, "rewardXp", 0),
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

    // ---------------------------------------------------------------------
    // chapter files
    // ---------------------------------------------------------------------

    private void readChapter(File file, List<NodeDef> nodes, List<ChoiceDef> choices) {
        JsonNode root = readFile(file);
        int chapter = integer(root, "chapter");
        for (JsonNode n : array(root, "nodes")) {
            String nodeId = text(n, "id");
            nodes.add(new NodeDef(nodeId, chapter, text(n, "location"), text(n, "type"), text(n, "title"),
                    text(n, "text"), optText(n, "encounter"), optText(n, "win"), optText(n, "lose"),
                    optText(n, "flee")));
            int sortOrder = 0;
            for (JsonNode c : array(n, "choices")) {
                choices.add(new ChoiceDef(nextChoiceId++, nodeId, text(c, "to"), text(c, "label"), sortOrder++,
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
        if (value == null || !value.isTextual()) {
            throw new ContentValidationException("QUEST condition in " + where
                    + " must use a status name such as \"COMPLETED\" (D13)");
        }
        try {
            return QuestStatus.valueOf(value.asText()).ordinal();
        } catch (IllegalArgumentException e) {
            throw new ContentValidationException("Unknown quest status '" + value.asText() + "' in " + where);
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
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new ContentValidationException("Missing '" + field + "' in " + describe(node));
        }
        return value.asText();
    }

    private static String optText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
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
            return "object '" + id.asText() + "'";
        }
        JsonNode label = node.get("label");
        return label != null ? "choice '" + label.asText() + "'" : "an object";
    }
}
