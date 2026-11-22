package com.shardbound.engine;

import com.shardbound.content.ContentLoader;
import com.shardbound.content.GameContent;
import com.shardbound.engine.port.RunStore;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Clock;

/** Test helpers: the real content and in-memory engines over it. */
public final class TestEngines {

    private static GameContent content;

    private TestEngines() {
    }

    /** The real content/ folder, loaded and validated once per test JVM. */
    public static synchronized GameContent content() {
        if (content == null) {
            content = ContentLoader.fromDirectory(Path.of("../content"));
        }
        return content;
    }

    /** An engine over the real content with no persistence. */
    public static GameEngine inMemory(Clock clock) {
        return withRuns(null, clock);
    }

    /** An engine over the real content that stores runs in {@code runs} (may be null). */
    public static GameEngine withRuns(@Nullable RunStore runs, Clock clock) {
        return new GameEngine(content(), null, null, runs, clock);
    }
}
