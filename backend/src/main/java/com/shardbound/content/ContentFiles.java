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
 * The raw text of every content file the server reads, keyed by its path
 * relative to the content folder ({@code "world.json"},
 * {@code "dialogue/maren.json"}, {@code "maps/eldmere_square.tmj"},
 * {@code "schema/world.schema.json"}). Art and audio belong to the client: of
 * {@code art/} and {@code audio/}, only their own top-level JSON documents are
 * kept, for the schema check.
 * Where the files came from (the jar or a folder on disk) is the caller's
 * business.
 */
public final class ContentFiles {

    public static final String WORLD = "world.json";
    public static final String REGIONS = "regions.json";
    public static final String BALANCE = "balance.json";
    public static final String SCHEMA_DIR = "schema/";
    public static final String DIALOGUE_DIR = "dialogue/";
    public static final String MAPS_DIR = "maps/";
    public static final String MAP_SUFFIX = ".tmj";
    /** Folders whose files the client ships; the server only schema-checks their top-level JSON. */
    private static final List<String> CLIENT_DIRS = List.of("art/", "audio/");

    private final SortedMap<String, String> texts;

    private ContentFiles(SortedMap<String, String> texts) {
        this.texts = Collections.unmodifiableSortedMap(texts);
    }

    public static ContentFiles of(Map<String, String> texts) {
        SortedMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            String name = entry.getKey().replace('\\', '/');
            if (clientDir(name) == null || isClientDocument(name)) {
                sorted.put(name, entry.getValue());
            }
        }
        return new ContentFiles(sorted);
    }

    private static String clientDir(String name) {
        return CLIENT_DIRS.stream().filter(name::startsWith).findFirst().orElse(null);
    }

    /**
     * A client folder's own JSON (the art manifest and grades, the audio
     * manifest) is schema-checked with the rest when present; it is not in the
     * server jar, which ships no art or audio.
     */
    private static boolean isClientDocument(String name) {
        String dir = clientDir(name);
        return dir != null && name.indexOf('/', dir.length()) < 0 && name.endsWith(".json");
    }

    /** Reads every {@code *.json} and {@code *.tmj} file under {@code dir}, except the client's art and audio. */
    public static ContentFiles fromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new ContentValidationException("Content folder not found: " + dir.toAbsolutePath());
        }
        SortedMap<String, String> texts = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                String file = path.getFileName().toString();
                if (Files.isRegularFile(path) && (file.endsWith(".json") || file.endsWith(MAP_SUFFIX))) {
                    texts.put(dir.relativize(path).toString().replace('\\', '/'), Files.readString(path, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read content folder " + dir, e);
        }
        return of(texts);
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

    /** The JSON content files that must name a schema: everything except the schemas and the maps. */
    public List<String> jsonDocuments() {
        List<String> names = new ArrayList<>();
        for (String name : texts.keySet()) {
            if (name.endsWith(".json") && !name.startsWith(SCHEMA_DIR)) {
                names.add(name);
            }
        }
        return names;
    }

    /** Dialogue files ({@code dialogue/*.json}), in name order. */
    public List<String> dialogueNames() {
        List<String> names = new ArrayList<>();
        for (String name : texts.keySet()) {
            if (name.startsWith(DIALOGUE_DIR) && name.endsWith(".json")) {
                names.add(name);
            }
        }
        return names;
    }

    /** Map texts keyed by map key (the file name without {@code .tmj}). */
    public Map<String, String> maps() {
        Map<String, String> maps = new TreeMap<>();
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(MAPS_DIR) && name.endsWith(MAP_SUFFIX)) {
                maps.put(name.substring(MAPS_DIR.length(), name.length() - MAP_SUFFIX.length()), entry.getValue());
            }
        }
        return maps;
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
