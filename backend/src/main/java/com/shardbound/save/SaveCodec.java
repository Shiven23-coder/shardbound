package com.shardbound.save;

import com.shardbound.exception.SaveIncompatibleException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * JSON for save slots and active runs. Reading always upgrades the document
 * to the current format first, so callers only ever see current records.
 *
 * <p>An active run keeps its saveable part under {@code "game"}. The same
 * upgrades apply to it, so a deploy that changes the save format also carries
 * every game in progress across.
 */
public class SaveCodec {

    private final JsonMapper mapper;
    private final SaveUpgrader upgrader;

    public SaveCodec(SaveUpgrader upgrader) {
        this.mapper = JsonMapper.builder().build();
        this.upgrader = upgrader;
    }

    public static SaveCodec standard() {
        return new SaveCodec(SaveUpgrader.standard());
    }

    public int currentVersion() {
        return upgrader.currentVersion();
    }

    public String writeSave(SaveSnapshot snapshot) {
        return mapper.writeValueAsString(snapshot);
    }

    public String writeRun(RunSnapshot snapshot) {
        return mapper.writeValueAsString(snapshot);
    }

    public SaveSnapshot readSave(String json, int storedVersion) {
        ObjectNode document = upgrader.upgrade(parseObject(json), storedVersion);
        return convert(document, SaveSnapshot.class);
    }

    public RunSnapshot readRun(String json, int storedVersion) {
        ObjectNode run = parseObject(json);
        JsonNode game = run.get("game");
        if (!(game instanceof ObjectNode gameObject)) {
            throw new SaveIncompatibleException("Stored game has no 'game' object.");
        }
        run.set("game", upgrader.upgrade(gameObject, storedVersion));
        if (storedVersion < 2 && "STORY".equals(run.path("mode").asString())) {
            run.put("mode", "DIALOGUE");    // format 2: v1 story pages are dialogue nodes
        }
        if (storedVersion < 3) {
            run.remove("combat");           // format 3: a v1 fight restarts as a battle (BattleStatsUpgrade)
            if ("COMBAT".equals(run.path("mode").asString())) {
                run.put("mode", "BATTLE");
            }
        }
        return convert(run, RunSnapshot.class);
    }

    private ObjectNode parseObject(String json) {
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (JacksonException e) {
            throw new SaveIncompatibleException("Stored game is not valid JSON: " + e.getOriginalMessage());
        }
        if (node instanceof ObjectNode object) {
            return object;
        }
        throw new SaveIncompatibleException("Stored game is not a JSON object.");
    }

    private <T> T convert(ObjectNode document, Class<T> type) {
        try {
            return mapper.treeToValue(document, type);
        } catch (JacksonException e) {
            throw new SaveIncompatibleException("Stored game does not match save format "
                    + upgrader.currentVersion() + ": " + e.getOriginalMessage());
        }
    }
}
