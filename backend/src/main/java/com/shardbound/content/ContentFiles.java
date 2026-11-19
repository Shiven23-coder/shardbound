package com.shardbound.content;

import com.shardbound.exception.ContentValidationException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The raw text of every content file, keyed by its path relative to the
 * content folder ({@code "world.json"}, {@code "ch0_prologue.json"},
 * {@code "schema/world.schema.json"}). Where the files came from (the jar or
 * a folder on disk) is the caller's business.
 */
public final class ContentFiles {

    public static final String WORLD = "world.json";
    public static final String SCHEMA_DIR = "schema/";

    private final SortedMap<String, String> texts;

    private ContentFiles(SortedMap<String, String> texts) {
        this.texts = Collections.unmodifiableSortedMap(texts);
    }

    public static ContentFiles of(Map<String, String> texts) {
        SortedMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            sorted.put(entry.getKey().replace('\\', '/'), entry.getValue());
        }
        return new ContentFiles(sorted);
    }

    /** Reads every {@code *.json} file under {@code dir}, including the schema folder. */
    public static ContentFiles fromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new ContentValidationException("Content folder not found: " + dir.toAbsolutePath());
        }
        SortedMap<String, String> texts = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")) {
                    String name = dir.relativize(path).toString().replace('\\', '/');
                    texts.put(name, Files.readString(path, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read content folder " + dir, e);
        }
        return new ContentFiles(texts);
    }

    public Map<String, String> all() {
        return texts;
    }

    public String text(String name) {
        String text = texts.get(name);
        if (text == null) {
            throw new ContentValidationException("Missing content file " + name);
        }
        return text;
    }

    /** Chapter files ({@code ch*.json} in the top folder), in name order. */
    public List<String> chapterNames() {
        List<String> names = new ArrayList<>();
        for (String name : texts.keySet()) {
            if (!name.contains("/") && name.startsWith("ch") && name.endsWith(".json")) {
                names.add(name);
            }
        }
        return names;
    }

    /** Schema files keyed by their file name inside {@code schema/}. */
    public Map<String, String> schemas() {
        Map<String, String> schemas = new TreeMap<>();
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            if (entry.getKey().startsWith(SCHEMA_DIR)) {
                schemas.put(entry.getKey().substring(SCHEMA_DIR.length()), entry.getValue());
            }
        }
        return schemas;
    }
}
