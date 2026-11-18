package com.shardbound.api;

import com.shardbound.achievement.Achievement;
import com.shardbound.api.dto.MetaViews.AchievementView;
import com.shardbound.api.dto.MetaViews.EndingInfoView;
import com.shardbound.api.dto.MetaViews.LeaderboardView;
import com.shardbound.engine.Ending;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.SessionStore;
import com.shardbound.persistence.MetaRepository;
import io.javalin.Javalin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Achievements, endings, leaderboard and the debug Inspector. Read-only. */
public class MetaController {

    private final GameEngine engine;
    private final SessionStore store;
    private final MetaRepository meta;

    public MetaController(GameEngine engine, SessionStore store, MetaRepository meta) {
        this.engine = engine;
        this.store = store;
        this.meta = meta;
    }

    public void register(Javalin app) {
        app.get("/api/achievements", ctx -> ctx.json(achievements(Sessions.playerId(ctx, store))));
        app.get("/api/endings", ctx -> ctx.json(endings(Sessions.playerId(ctx, store))));
        app.get("/api/leaderboard", ctx -> ctx.json(leaderboard()));
        app.get("/api/debug/inspector", ctx -> ctx.json(engine.inspect(Sessions.require(ctx, store))));
    }

    private List<AchievementView> achievements(int playerId) {
        Set<String> unlocked = meta.unlockedFor(playerId);
        List<AchievementView> result = new ArrayList<>();
        for (Achievement a : engine.content().achievements().values()) {
            result.add(new AchievementView(a.id(), a.title(), a.description(), a.hidden(), unlocked.contains(a.id())));
        }
        return result;
    }

    private List<EndingInfoView> endings(int playerId) {
        Map<String, Integer> reached = meta.endingsFor(playerId);
        List<EndingInfoView> result = new ArrayList<>();
        for (Ending e : engine.content().endings()) {
            result.add(new EndingInfoView(e.id(), e.title(), reached.containsKey(e.id()), reached.get(e.id())));
        }
        return result;
    }

    private List<LeaderboardView> leaderboard() {
        List<LeaderboardView> result = new ArrayList<>();
        for (MetaRepository.LeaderboardRow row : meta.leaderboard()) {
            result.add(new LeaderboardView(row.username(), row.endings(), row.achievements(),
                    row.fastestEndingSeconds()));
        }
        return result;
    }
}
