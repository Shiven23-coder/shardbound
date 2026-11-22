package com.shardbound.content;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Art and audio belong to the client: of those folders only their own top-level JSON is kept, for the schema check. */
class ContentFilesFolderTest {

    @Test
    void keepsOnlyTheTopLevelJsonOfTheClientFolders() {
        ContentFiles files = ContentFiles.of(Map.of(
                "world.json", "{}",
                "maps/eldmere_square.tmj", "{}",
                "art/manifest.json", "{}",
                "art/ninja-adventure/eldmere/tiles.json", "{}",
                "audio/audio.json", "{}",
                "audio/sfx/notes.json", "{}"));

        assertEquals(Set.of("world.json", "maps/eldmere_square.tmj", "art/manifest.json", "audio/audio.json"),
                files.all().keySet());
    }
}
