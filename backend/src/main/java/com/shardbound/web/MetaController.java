package com.shardbound.web;

import com.shardbound.achievement.Achievement;
import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.Ending;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.persistence.MetaRepository;
import com.shardbound.view.MetaViews.AchievementView;
import com.shardbound.view.MetaViews.EndingInfoView;
import com.shardbound.view.MetaViews.LeaderboardView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Achievements, endings and the leaderboard. Read-only. */
@RestController
@RequestMapping("/api")
@Tag(name = "meta")
public class MetaController {

    private final GameEngine engine;
    private final MetaRepository meta;

    public MetaController(GameEngine engine, MetaRepository meta) {
        this.engine = engine;
        this.meta = meta;
    }

    @GetMapping("/achievements")
    @Operation(operationId = "listAchievements", summary = "Every achievement with this player's unlocks")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public List<AchievementView> achievements(@CurrentGame(required = false) @Nullable GameState state,
                                              @RequestParam(required = false) @Nullable Integer playerId) {
        Set<String> unlocked = meta.unlockedFor(PlayerIds.resolve(state, playerId));
        List<AchievementView> result = new ArrayList<>();
        for (Achievement a : engine.content().achievements().values()) {
            result.add(new AchievementView(a.id(), a.title(), a.description(), a.hidden(), unlocked.contains(a.id())));
        }
        return result;
    }

    @GetMapping("/endings")
    @Operation(operationId = "listEndings", summary = "Every ending with this player's best times")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public List<EndingInfoView> endings(@CurrentGame(required = false) @Nullable GameState state,
                                        @RequestParam(required = false) @Nullable Integer playerId) {
        Map<String, Integer> reached = meta.endingsFor(PlayerIds.resolve(state, playerId));
        List<EndingInfoView> result = new ArrayList<>();
        for (Ending e : engine.content().endings()) {
            result.add(new EndingInfoView(e.id(), e.title(), reached.containsKey(e.id()), reached.get(e.id())));
        }
        return result;
    }

    @GetMapping("/leaderboard")
    @Operation(operationId = "leaderboard", summary = "Top players by endings, achievements and fastest ending")
    public List<LeaderboardView> leaderboard() {
        List<LeaderboardView> result = new ArrayList<>();
        for (MetaRepository.LeaderboardRow row : meta.leaderboard()) {
            result.add(new LeaderboardView(row.username(), row.endings(), row.achievements(),
                    row.fastestEndingSeconds()));
        }
        return result;
    }
}
