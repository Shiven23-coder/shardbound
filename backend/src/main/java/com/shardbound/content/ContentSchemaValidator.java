package com.shardbound.content;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks every JSON content file against the JSON Schema (draft 2020-12) it
 * names in its {@code "$schema"} key, from {@code content/schema/} (§9). Each
 * problem names the file and the exact place in it, for example
 * {@code dialogue/maren.json /nodes/3/type: does not have a value in the
 * enumeration [...]}.
 *
 * <p>Schemas reference each other by {@code $id}; they are all registered from
 * the content files themselves, so validation never touches the network.
 */
public class ContentSchemaValidator {

    public static final String SCHEMA_BASE = "https://shardbound.game/schema/";
    private static final List<String> REQUIRED = List.of(ContentFiles.WORLD, ContentFiles.REGIONS, ContentFiles.BALANCE,
            ContentFiles.ELEMENTS, ContentFiles.STATUSES, ContentFiles.SKILLS, ContentFiles.CREEPS,
            ContentFiles.BOSSES);

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** All problems found; empty when every file is valid. */
    public List<String> validate(ContentFiles files) {
        Map<String, String> byIri = new HashMap<>();
        for (Map.Entry<String, String> schema : files.schemas().entrySet()) {
            byIri.put(SCHEMA_BASE + schema.getKey(), schema.getValue());
        }
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(byIri));
        List<String> problems = new ArrayList<>();
        for (String required : REQUIRED) {
            if (!files.all().containsKey(required)) {
                problems.add(required + " is missing");
            }
        }
        if (files.dialogueNames().isEmpty()) {
            problems.add("no dialogue files (dialogue/*.json) found");
        }
        for (String name : files.jsonDocuments()) {
            check(name, files.text(name), byIri, registry, problems);
        }
        return problems;
    }

    private void check(String fileName, String text, Map<String, String> byIri, SchemaRegistry registry,
                       List<String> problems) {
        JsonNode document;
        try {
            document = mapper.readTree(text);
        } catch (JacksonException e) {
            problems.add(fileName + ": not valid JSON: " + e.getOriginalMessage());
            return;
        }
        JsonNode declared = document.get("$schema");
        if (declared == null || !declared.isString()) {
            problems.add(fileName + ": names no \"$schema\"");
            return;
        }
        String schemaFile = declared.asString().substring(declared.asString().lastIndexOf('/') + 1);
        if (!byIri.containsKey(SCHEMA_BASE + schemaFile)) {
            problems.add(fileName + ": its schema " + schemaFile + " is not in schema/");
            return;
        }
        Schema schema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + schemaFile));
        for (Error error : schema.validate(document)) {
            problems.add(fileName + " " + error.getInstanceLocation() + ": " + error.getMessage());
        }
    }
}
