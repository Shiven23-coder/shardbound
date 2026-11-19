package com.shardbound.support;

import com.shardbound.engine.TestEngines;
import com.shardbound.story.Choice;
import com.shardbound.story.StoryGraph;
import com.shardbound.support.TestApi.Response;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Plays the quick route over HTTP, exactly as the browser would: read the
 * view, pick the unlocked choice that leads to the next route node, attack
 * in fights.
 */
public final class QuickRouteDriver {

    private QuickRouteDriver() {
    }

    /** Plays until {@code stop} matches the current view or the game ends; returns the last view. */
    public static JsonNode playUntil(TestApi api, StoryGraph graph, String session, JsonNode view,
                                     Predicate<JsonNode> stop) {
        int guard = 0;
        while (!stop.test(view) && !"ENDED".equals(view.get("mode").asString()) && guard++ < 200) {
            Response next;
            if ("COMBAT".equals(view.get("mode").asString())) {
                next = api.post("/api/combat/action", session, Map.of("type", "ATTACK", "target", 0));
            } else {
                next = api.post("/api/game/choose", session, Map.of("choiceId", nextChoice(graph, view)));
            }
            TestApi.expect(200, next);
            view = next.body();
        }
        return view;
    }

    private static int nextChoice(StoryGraph graph, JsonNode view) {
        String node = view.get("node").get("id").asString();
        int index = TestEngines.QUICK_ROUTE.indexOf(node);
        if (index < 0 || index + 1 >= TestEngines.QUICK_ROUTE.size()) {
            throw new AssertionError("Left the quick route at " + node);
        }
        String target = TestEngines.QUICK_ROUTE.get(index + 1);
        for (JsonNode choice : view.get("choices")) {
            int id = choice.get("id").asInt();
            boolean leadsThere = graph.choice(node, id).map(Choice::to).map(target::equals).orElse(false);
            if (leadsThere && !choice.get("locked").asBoolean()) {
                return id;
            }
        }
        throw new AssertionError("No unlocked choice from " + node + " to " + target);
    }
}
