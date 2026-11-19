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
 * Checks every content file against its JSON Schema (draft 2020-12) in
 * {@code content/schema/}. Each problem names the file and the exact place in
 * it, for example {@code ch0_prologue.json /nodes/3/type: does not have a
 * value in the enumeration [...]}.
 *
 * <p>Schemas reference each other by {@code $id}; they are all registered from
 * the content files themselves, so validation never touches the network.
 */
public class ContentSchemaValidator {

    public static final String SCHEMA_BASE = "https://shardbound.game/schema/";
    public static final String WORLD_SCHEMA = "world.schema.json";
    public static final String CHAPTER_SCHEMA = "chapter.schema.json";

    private final JsonMapper mapper = JsonMapper.builder().build();

    /** All problems found; empty when every file is valid. */
    public List<String> validate(ContentFiles files) {
        Map<String, String> byIri = new HashMap<>();
        for (Map.Entry<String, String> schema : files.schemas().entrySet()) {
            byIri.put(SCHEMA_BASE + schema.getKey(), schema.getValue());
        }
        List<String> problems = new ArrayList<>();
        if (!byIri.containsKey(SCHEMA_BASE + WORLD_SCHEMA) || !byIri.containsKey(SCHEMA_BASE + CHAPTER_SCHEMA)) {
            problems.add("schema/" + WORLD_SCHEMA + " and schema/" + CHAPTER_SCHEMA + " are required");
            return problems;
        }
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemas(byIri));
        Schema worldSchema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + WORLD_SCHEMA));
        Schema chapterSchema = registry.getSchema(SchemaLocation.of(SCHEMA_BASE + CHAPTER_SCHEMA));

        if (!files.all().containsKey(ContentFiles.WORLD)) {
            problems.add(ContentFiles.WORLD + " is missing");
        } else {
            check(ContentFiles.WORLD, files.text(ContentFiles.WORLD), worldSchema, problems);
        }
        List<String> chapters = files.chapterNames();
        if (chapters.isEmpty()) {
            problems.add("no chapter files (ch*.json) found");
        }
        for (String chapter : chapters) {
            check(chapter, files.text(chapter), chapterSchema, problems);
        }
        return problems;
    }

    private void check(String fileName, String text, Schema schema, List<String> problems) {
        JsonNode document;
        try {
            document = mapper.readTree(text);
        } catch (JacksonException e) {
            problems.add(fileName + ": not valid JSON: " + e.getOriginalMessage());
            return;
        }
        for (Error error : schema.validate(document)) {
            problems.add(fileName + " " + error.getInstanceLocation() + ": " + error.getMessage());
        }
    }
}
