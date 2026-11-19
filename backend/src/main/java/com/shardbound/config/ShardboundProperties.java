package com.shardbound.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under {@code shardbound.*} (env vars {@code SHARDBOUND_*}).
 *
 * @param contentDir   read content from this folder instead of the jar (dev hot editing)
 * @param runCache     in-memory cache of games in progress
 * @param runRetention runs untouched for this long are deleted by the daily cleanup
 */
@Validated
@ConfigurationProperties("shardbound")
public record ShardboundProperties(
        @Nullable String contentDir,
        @Valid @NotNull RunCache runCache,
        @NotNull Duration runRetention) {

    /**
     * @param maxSize     most games kept in memory at once
     * @param idleTimeout a game not used for this long is dropped from memory (it stays in the database)
     */
    public record RunCache(@Min(1) int maxSize, @NotNull Duration idleTimeout) { }
}
