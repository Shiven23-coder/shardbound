package com.shardbound.content;

import com.shardbound.content.RawContent.RuleDef;
import com.shardbound.cutscene.Cutscene;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.rules.RuleFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads {@code content/cutscenes/*.json} (P-68), already checked against {@code cutscene.schema.json}. The
 * {@code say} variants' conditions become condition groups once the rule factory exists, so reading is in two
 * steps: {@link #read} keeps them as rule rows and {@link #build} makes the cutscenes.
 */
public final class CutsceneReader {

    /** A cutscene as read, its steps' variants still rule rows. */
    public record RawCutscene(String id, String area, List<Cutscene.Actor> actors, List<RawStep> steps, String then) { }

    public record RawStep(Cutscene.Step step, List<RawVariant> variants) { }

    public record RawVariant(List<RuleDef> conditions, String text) { }

    private final JsonMapper mapper = JsonMapper.builder().build();

    public List<RawCutscene> read(ContentFiles files) {
        List<RawCutscene> result = new ArrayList<>();
        for (String name : files.cutsceneNames()) {
            JsonNode n;
            try {
                n = mapper.readTree(files.text(name));
            } catch (JacksonException e) {
                throw new ContentValidationException("Cannot read " + name + ": " + e.getOriginalMessage());
            }
            List<Cutscene.Actor> actors = new ArrayList<>();
            for (JsonNode a : n.path("actors")) {
                actors.add(new Cutscene.Actor(a.get("id").asString(), a.get("sprite").asString(), a.get("x").asInt(),
                        a.get("y").asInt(), a.path("facing").asString("DOWN"), text(a, "name")));
            }
            List<RawStep> steps = new ArrayList<>();
            for (JsonNode s : n.get("steps")) {
                steps.add(step(s));
            }
            result.add(new RawCutscene(n.get("id").asString(), n.get("area").asString(), actors, steps,
                    n.get("then").asString()));
        }
        return result;
    }

    private static RawStep step(JsonNode s) {
        List<Cutscene.Tile> path = new ArrayList<>();
        for (JsonNode t : s.path("path")) {
            path.add(new Cutscene.Tile(t.get(0).asInt(), t.get(1).asInt()));
        }
        List<RawVariant> variants = new ArrayList<>();
        for (JsonNode v : s.path("variants")) {
            List<RuleDef> conditions = new ArrayList<>();
            for (JsonNode c : v.get("conditions")) {
                conditions.add(new RuleDef(c.get("type").asString(), c.get("key").asString(), c.get("op").asString(),
                        c.get("value").asInt(), c.path("grp").asInt(0)));
            }
            variants.add(new RawVariant(conditions, v.get("text").asString()));
        }
        Cutscene.Step step = new Cutscene.Step(Cutscene.Kind.valueOf(s.get("do").asString().toUpperCase(Locale.ROOT)),
                text(s, "actor"), text(s, "speaker"), text(s, "text"), List.of(), path, text(s, "facing"),
                text(s, "camera"), s.has("x") ? s.get("x").asInt() : null, s.has("y") ? s.get("y").asInt() : null,
                s.has("zoom") ? s.get("zoom").asDouble() : null, s.has("ms") ? s.get("ms").asInt() : null,
                text(s, "track"), text(s, "effect"), text(s, "state"));
        return new RawStep(step, variants);
    }

    private static String text(JsonNode n, String field) {
        return n.has(field) ? n.get(field).asString() : null;
    }

    /** The cutscenes by id, their variants' conditions built by {@code rules}. */
    public static Map<String, Cutscene> build(List<RawCutscene> raw, RuleFactory rules) {
        Map<String, Cutscene> result = new LinkedHashMap<>();
        for (RawCutscene c : raw) {
            List<Cutscene.Step> steps = new ArrayList<>();
            for (RawStep s : c.steps()) {
                Cutscene.Step base = s.step();
                List<Cutscene.Variant> variants = s.variants().stream()
                        .map(v -> new Cutscene.Variant(rules.group(v.conditions()), v.text())).toList();
                steps.add(new Cutscene.Step(base.kind(), base.actor(), base.speaker(), base.text(), variants, base.path(),
                        base.facing(), base.camera(), base.x(), base.y(), base.zoom(), base.ms(), base.track(),
                        base.effect(), base.state()));
            }
            if (result.put(c.id(), new Cutscene(c.id(), c.area(), c.actors(), steps, c.then())) != null) {
                throw new ContentValidationException("cutscenes: " + c.id() + " is defined twice");
            }
        }
        return result;
    }
}
