package com.shardbound.content;

import com.shardbound.battle.Affinity;
import com.shardbound.battle.BattleContent;
import com.shardbound.battle.BattleRules;
import com.shardbound.battle.BossTemplate;
import com.shardbound.battle.CreepTemplate;
import com.shardbound.battle.Element;
import com.shardbound.battle.ElementChart;
import com.shardbound.battle.Family;
import com.shardbound.battle.Skill;
import com.shardbound.battle.Status;
import com.shardbound.battle.StatusRule;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.progression.Curve;
import com.shardbound.progression.ProgressionRules;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the battle content (elements.json, statuses.json, skills.json,
 * creeps.json and balance.json {@code battle}) and the progression rules
 * (balance.json {@code progression}). The schemas have checked the shapes;
 * this checks references (a creep's moves name skills) and builds the
 * immutable objects.
 */
public class BattleContentReader {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ContentFiles files;

    public BattleContentReader(ContentFiles files) {
        this.files = files;
    }

    public BattleContent readBattle() {
        Map<String, Skill> skills = readSkills();
        return new BattleContent(readChart(), readStatuses(), skills, readCreeps(skills), readBosses(skills), readRules());
    }

    private ElementChart readChart() {
        JsonNode root = parse(ContentFiles.ELEMENTS);
        Map<Affinity, Double> multipliers = new EnumMap<>(Affinity.class);
        for (Affinity affinity : Affinity.values()) {
            multipliers.put(affinity, root.get("multipliers").get(affinity.name()).asDouble());
        }
        Map<Family, EnumMap<Element, Affinity>> chart = new EnumMap<>(Family.class);
        for (Family family : Family.values()) {
            EnumMap<Element, Affinity> row = new EnumMap<>(Element.class);
            for (Element element : Element.values()) {
                row.put(element, Affinity.valueOf(root.get("chart").get(family.name()).get(element.name()).asString()));
            }
            chart.put(family, row);
        }
        return new ElementChart(chart, multipliers);
    }

    private Map<Status, StatusRule> readStatuses() {
        JsonNode root = parse(ContentFiles.STATUSES).get("statuses");
        Map<Status, StatusRule> result = new EnumMap<>(Status.class);
        for (Status status : Status.values()) {
            JsonNode n = root.get(status.name());
            Set<Family> immune = EnumSet.noneOf(Family.class);
            for (JsonNode family : n.get("immuneFamilies")) {
                immune.add(Family.valueOf(family.asString()));
            }
            String removedBy = optText(n, "removedBy");
            result.put(status, new StatusRule(status, n.get("name").asString(), n.get("letter").asString(),
                    n.get("icon").asString(), n.get("turns").asInt(), optInt(n, "burstPct"), optInt(n, "tickPct"),
                    optInt(n, "defencePct"), optInt(n, "speed"), removedBy == null ? null : Element.valueOf(removedBy),
                    n.has("skipsTurn") && n.get("skipsTurn").asBoolean(), optInt(n, "immuneTurnsAfter"), immune));
        }
        return result;
    }

    private Map<String, Skill> readSkills() {
        Map<String, Skill> result = new LinkedHashMap<>();
        for (JsonNode n : parse(ContentFiles.SKILLS).get("skills")) {
            String id = n.get("id").asString();
            String status = optText(n, "status");
            Skill skill = new Skill(id, n.get("name").asString(), n.get("description").asString(),
                    Element.valueOf(n.get("element").asString()), n.get("power").asDouble(), n.get("fpCost").asInt(),
                    Skill.Scaling.valueOf(n.get("scaling").asString()), status == null ? null : Status.valueOf(status),
                    optInt(n, "statusChance"), n.has("telegraph") && n.get("telegraph").asBoolean(),
                    optText(n, "telegraphLine"), Skill.Unlock.valueOf(n.get("unlock").asString()));
            if (result.put(id, skill) != null) {
                throw new ContentValidationException("skills.json: skill " + id + " is defined twice");
            }
        }
        return result;
    }

    private Map<String, CreepTemplate> readCreeps(Map<String, Skill> skills) {
        Map<String, CreepTemplate> result = new LinkedHashMap<>();
        for (JsonNode n : parse(ContentFiles.CREEPS).get("creeps")) {
            String id = n.get("id").asString();
            List<CreepTemplate.WeightedMove> moves = new ArrayList<>();
            for (JsonNode m : n.get("moves")) {
                Skill skill = skills.get(m.get("skill").asString());
                if (skill == null) {
                    throw new ContentValidationException("creeps.json: " + id + " uses unknown skill " + m.get("skill").asString());
                }
                moves.add(new CreepTemplate.WeightedMove(skill, m.get("weight").asInt()));
            }
            List<CreepTemplate.Drop> drops = new ArrayList<>();
            for (JsonNode d : n.get("drops")) {
                drops.add(new CreepTemplate.Drop(d.get("item").asString(), d.get("pct").asInt()));
            }
            CreepTemplate creep = new CreepTemplate(id, n.get("name").asString(), Family.valueOf(n.get("family").asString()),
                    n.get("sprite").asString(), n.get("baseHp").asInt(), n.get("baseAtk").asInt(),
                    n.get("baseDef").asInt(), n.get("baseSpd").asInt(),
                    CreepTemplate.Strategy.valueOf(n.get("ai").asString()), moves, drops);
            if (result.put(id, creep) != null) {
                throw new ContentValidationException("creeps.json: creep " + id + " is defined twice");
            }
        }
        return result;
    }

    /** content/bosses.json (P-67): two phases each, their moves and telegraphs resolved to skills. */
    private Map<String, BossTemplate> readBosses(Map<String, Skill> skills) {
        Map<String, BossTemplate> result = new LinkedHashMap<>();
        for (JsonNode n : parse(ContentFiles.BOSSES).get("bosses")) {
            String id = n.get("id").asString();
            List<BossTemplate.Phase> phases = new ArrayList<>();
            for (JsonNode p : n.get("phases")) {
                List<CreepTemplate.WeightedMove> moves = new ArrayList<>();
                for (JsonNode m : p.get("moves")) {
                    moves.add(new CreepTemplate.WeightedMove(bossSkill(skills, id, m.get("skill").asString()), m.get("weight").asInt()));
                }
                Skill telegraph = bossSkill(skills, id, p.get("telegraph").asString());
                if (!telegraph.telegraph()) {
                    throw new ContentValidationException("bosses.json: " + id + " telegraphs " + telegraph.id()
                            + ", which is not a telegraphed move");
                }
                phases.add(new BossTemplate.Phase(p.path("atPct").asInt(100), p.has("line") ? p.get("line").asString() : null,
                        p.path("spdDelta").asInt(0), moves, telegraph, p.get("telegraphEvery").asInt()));
            }
            if (phases.get(1).line() == null) {
                throw new ContentValidationException("bosses.json: " + id + "'s second phase needs its line");
            }
            JsonNode expose = n.get("onGuardedTelegraph");
            JsonNode reward = n.get("reward");
            BossTemplate boss = new BossTemplate(id, n.get("name").asString(), Family.valueOf(n.get("family").asString()),
                    n.get("sprite").asString(), n.get("level").asInt(), n.get("baseHp").asInt(), n.get("baseAtk").asInt(),
                    n.get("baseDef").asInt(), n.get("baseSpd").asInt(), phases, Status.valueOf(expose.get("status").asString()),
                    expose.get("line").asString(), reward.get("item").asString(), reward.get("fallbackLevel").asInt(),
                    n.get("defeatFlag").asString(), n.get("introCutscene").asString(), n.get("epilogueCutscene").asString());
            if (result.put(id, boss) != null) {
                throw new ContentValidationException("bosses.json: boss " + id + " is defined twice");
            }
        }
        return result;
    }

    private static Skill bossSkill(Map<String, Skill> skills, String bossId, String skillId) {
        Skill skill = skills.get(skillId);
        if (skill == null) {
            throw new ContentValidationException("bosses.json: " + bossId + " uses unknown skill " + skillId);
        }
        return skill;
    }

    private BattleRules readRules() {
        JsonNode b = parse(ContentFiles.BALANCE).get("battle");
        JsonNode flee = b.get("flee");
        return new BattleRules(b.get("creepScaling").get("hp").asDouble(), b.get("creepScaling").get("atk").asDouble(),
                b.get("creepScaling").get("def").asDouble(), b.get("creepShards").get("base").asDouble(),
                b.get("creepShards").get("exponent").asDouble(), b.get("roll").get("min").asDouble(),
                b.get("roll").get("max").asDouble(), b.get("critMultiplier").asDouble(), b.get("enemyCritPct").asInt(),
                b.get("guard").get("normal").asDouble(), b.get("guard").get("telegraphed").asDouble(),
                b.get("statusChanceCap").asInt(), flee.get("basePct").asInt(), flee.get("pctPerSpeed").asInt(),
                flee.get("minPct").asInt(), flee.get("maxPct").asInt(), flee.get("ignoreTiles").asInt(),
                b.get("defensiveGuardBelowPct").asInt(), b.get("dialogueLossHpPct").asInt(),
                b.get("victoryFpPct").asInt(), b.get("bossShardsMultiplier").asInt());
    }

    public ProgressionRules readProgression() {
        JsonNode p = parse(ContentFiles.BALANCE).get("progression");
        JsonNode curves = p.get("curves");
        Map<String, Double> grades = new HashMap<>();
        p.get("gradeCoefficients").properties().forEach(e -> grades.put(e.getKey(), e.getValue().asDouble()));
        return new ProgressionRules(p.get("attributeStart").asInt(), curve(curves, "vigorHp"), curve(curves, "mindFp"),
                curve(curves, "enduranceDef"), curve(curves, "enduranceResistPct"), curve(curves, "scalingSat"),
                curve(curves, "arcaneStatusPct"), p.get("speed").get("base").asInt(),
                p.get("speed").get("dexPerPoint").asInt(), p.get("crit").get("dexPerPct").asInt(),
                p.get("crit").get("maxBonusPct").asInt(), p.get("spellPower").get("base").asDouble(),
                p.get("spellPower").get("range").asDouble(), grades, p.get("upgrade").get("base").asDouble(),
                p.get("upgrade").get("scaling").asDouble(), levels(p.get("levels")));
    }

    private static ProgressionRules.LevelRules levels(JsonNode l) {
        return new ProgressionRules.LevelRules(l.get("costBase").asDouble(), l.get("costExponent").asDouble(),
                l.get("maxLevel").asInt(), l.get("attributeMax").asInt());
    }

    private static Curve curve(JsonNode curves, String name) {
        Map<Integer, Double> points = new TreeMap<>();
        for (JsonNode point : curves.get(name)) {
            points.put(point.get(0).asInt(), point.get(1).asDouble());
        }
        return new Curve(points);
    }

    private JsonNode parse(String fileName) {
        try {
            return mapper.readTree(files.text(fileName));
        } catch (JacksonException e) {
            throw new ContentValidationException("Cannot read " + fileName + ": " + e.getOriginalMessage());
        }
    }

    private static String optText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static int optInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? 0 : value.asInt();
    }
}
