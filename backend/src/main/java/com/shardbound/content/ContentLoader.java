package com.shardbound.content;

import com.shardbound.exception.ContentValidationException;
import com.shardbound.story.StoryValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Turns the content files into the immutable {@link GameContent} the engine
 * plays (P-04). Runs once at startup and stops it on any problem:
 * <ol>
 *   <li>JSON Schema validation of every file (shape, types, enums);</li>
 *   <li>reading the JSON into {@link RawContent};</li>
 *   <li>story checks (reachability, dead ends, quick route, flags, quests);</li>
 *   <li>assembling the objects and ordering the quests (cycles are rejected).</li>
 * </ol>
 */
public class ContentLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ContentLoader.class);

    private final ContentSchemaValidator schemaValidator = new ContentSchemaValidator();

    public GameContent load(ContentFiles files) {
        List<String> problems = schemaValidator.validate(files);
        if (!problems.isEmpty()) {
            throw new ContentValidationException("Content does not match its JSON Schema", problems);
        }
        RawContent raw = new JsonContentReader(files).read();
        StoryValidator validator = new StoryValidator(raw);
        if (!validator.validate()) {
            throw new ContentValidationException("Story content failed validation", validator.errors());
        }
        for (String warning : validator.warnings()) {
            LOG.warn("Content: {}", warning);
        }
        GameContent content = new ContentAssembler().assemble(raw);
        content.prepareForPlay();
        LOG.info("Content loaded: {} nodes, {} items, {} quests", content.graph().nodes().size(),
                content.items().size(), content.questManager().quests().size());
        return content;
    }

    /** Convenience for tests and tools: load straight from a content folder. */
    public static GameContent fromDirectory(Path dir) {
        return new ContentLoader().load(ContentFiles.fromDirectory(dir));
    }
}
