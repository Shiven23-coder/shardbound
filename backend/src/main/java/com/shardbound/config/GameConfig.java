package com.shardbound.config;

import com.shardbound.content.ContentFiles;
import com.shardbound.content.ContentLoader;
import com.shardbound.content.GameContent;
import com.shardbound.engine.GameEngine;
import com.shardbound.persistence.ActiveRunRepository;
import com.shardbound.persistence.MetaRepository;
import com.shardbound.persistence.SaveRepository;
import com.shardbound.save.SaveCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Wires the framework-free engine into Spring: content, save codec and the engine itself. */
@Configuration(proxyBeanMethods = false)
public class GameConfig {

    static final String CLASSPATH_CONTENT = "classpath:content/";

    /** Content from {@code shardbound.content-dir} if set, otherwise from the jar. Fails startup on any problem. */
    @Bean
    GameContent gameContent(ShardboundProperties properties) {
        ContentFiles files = properties.contentDir() != null
                ? ContentFiles.fromDirectory(Path.of(properties.contentDir()))
                : classpathContent();
        return new ContentLoader().load(files);
    }

    @Bean
    SaveCodec saveCodec() {
        return SaveCodec.standard();
    }

    @Bean(destroyMethod = "shutdown")
    GameEngine gameEngine(GameContent content, SaveRepository saves, MetaRepository meta, ActiveRunRepository runs) {
        return new GameEngine(content, saves, meta, runs);
    }

    /** Every {@code *.json} under {@code content/} in the jar, keyed by its path inside that folder. */
    static ContentFiles classpathContent() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(CLASSPATH_CONTENT + "**/*.json");
            Map<String, String> texts = new HashMap<>();
            for (Resource resource : resources) {
                String url = resource.getURL().toString();
                int start = url.lastIndexOf("/content/") + "/content/".length();
                texts.put(url.substring(start), resource.getContentAsString(StandardCharsets.UTF_8));
            }
            return ContentFiles.of(texts);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read content from the classpath", e);
        }
    }
}
