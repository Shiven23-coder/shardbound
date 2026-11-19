package com.shardbound.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shardbound.config.ShardboundProperties;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.exception.SessionNotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Games in progress, by session token. Memory is only a cache (P-22): on a
 * miss the game is rebuilt from {@code active_run}, so a restarted server
 * picks up exactly where the player left off.
 *
 * <p>Caffeine bounds the cache and drops games idle for longer than
 * {@code shardbound.run-cache.idle-timeout}; they stay in the database.
 */
@Component
public class GameSessions {

    private final GameEngine engine;
    private final Cache<String, Optional<GameState>> cache;

    public GameSessions(GameEngine engine, ShardboundProperties properties) {
        this.engine = engine;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.runCache().maxSize())
                .expireAfterAccess(properties.runCache().idleTimeout())
                .build();
    }

    /** The game for this token, or {@link SessionNotFoundException} (404). */
    public GameState require(@Nullable String sessionId) {
        return find(sessionId).orElseThrow(SessionNotFoundException::new);
    }

    /** The game for this token if there is one. Unknown tokens are not cached. */
    public Optional<GameState> find(@Nullable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        Optional<GameState> state = cache.get(sessionId, engine::resume);
        if (state.isEmpty()) {
            cache.invalidate(sessionId);
        }
        return state;
    }

    public void put(GameState state) {
        cache.put(state.sessionId(), Optional.of(state));
    }

    /** Forgets the in-memory copy; the next request reloads it from the database. */
    public void evict(@Nullable String sessionId) {
        if (sessionId != null) {
            cache.invalidate(sessionId);
        }
    }
}
