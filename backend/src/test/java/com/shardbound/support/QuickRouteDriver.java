package com.shardbound.support;

import com.shardbound.content.GameContent;
import com.shardbound.support.TestApi.Response;
import com.shardbound.world.AreaMap;
import com.shardbound.world.MapObject;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plays {@link QuickRoute} steps over HTTP, exactly as the browser would:
 * dialogue choices by label, walking up to exits and objects and reporting
 * the position (with the server's test clock moved on by the walking time),
 * and attacking in fights.
 */
public final class QuickRouteDriver {

    private static final Duration WALK = Duration.ofMinutes(1);

    private final TestApi api;
    private final GameContent content;
    private final MutableClock clock;
    private final String session;

    public QuickRouteDriver(TestApi api, GameContent content, MutableClock clock, String session) {
        this.api = api;
        this.content = content;
        this.clock = clock;
        this.session = session;
    }

    /** Plays the steps until {@code stop} matches the current view or the game ends; returns the last view. */
    public JsonNode playUntil(JsonNode view, List<QuickRoute.Step> steps, Predicate<JsonNode> stop) {
        JsonNode current = view;
        for (QuickRoute.Step step : steps) {
            if (stop.test(current) || "ENDED".equals(current.get("mode").asString())) {
                return current;
            }
            current = play(current, step);
            if (stop.test(current)) {
                return current;
            }
            current = fight(current);
        }
        return current;
    }

    private JsonNode play(JsonNode view, QuickRoute.Step step) {
        Response next = switch (step) {
            case QuickRoute.Choose c -> api.post("/api/dialogue/choose", session, Map.of("choiceId", choice(view, c.label())));
            case QuickRoute.Exit e -> worldEvent(view, "/api/world/exit", "exitId", e.exitId());
            case QuickRoute.Interact i -> worldEvent(view, "/api/world/interact", "targetId", i.targetId());
        };
        TestApi.expect(200, next);
        return next.body();
    }

    private Response worldEvent(JsonNode view, String path, String idField, String objectName) {
        JsonNode world = view.get("world");
        AreaMap map = content.world().mapOf(world.get("areaId").asString());
        MapObject target = map.object(objectName).orElseThrow(() -> new AssertionError(objectName + " not in " + map.key()));
        JsonNode player = world.get("player");
        Set<String> hidden = new HashSet<>();
        world.get("hiddenObjects").forEach(name -> hidden.add(name.asString()));
        int[] spot = QuickRoute.standingSpot(map, map.collisionWith(o -> !hidden.contains(o.name())), target,
                map.tileOf(player.get("x").asInt()),
                map.tileOf(player.get("y").asInt()), content.worldRules().interactRange());
        clock.advance(WALK);
        return api.post(path, session, Map.of(idField, objectName, "x", spot[0], "y", spot[1],
                "facing", player.get("facing").asString()));
    }

    private static int choice(JsonNode view, String label) {
        for (JsonNode c : view.get("dialogue").get("choices")) {
            if (c.get("label").asString().startsWith(label) && !c.get("locked").asBoolean()) {
                return c.get("id").asInt();
            }
        }
        throw new AssertionError("No unlocked choice '" + label + "' in " + view.get("dialogue"));
    }

    /** Fights as the balance bot does (P-55): Strike, and a draught below 40 % HP while one is left. */
    private JsonNode fight(JsonNode view) {
        JsonNode current = view;
        while ("BATTLE".equals(current.get("mode").asString())) {
            JsonNode battle = current.get("battle");
            JsonNode player = battle.get("player");
            boolean low = player.get("hp").asInt() * 100 < player.get("maxHp").asInt() * 40;
            boolean draught = false;
            for (JsonNode item : battle.get("menu").get("bag")) {
                draught |= "lantern_draught".equals(item.get("id").asString()) && item.get("qty").asInt() > 0;
            }
            Map<String, Object> action = low && draught
                    ? Map.of("kind", "ITEM", "slot", 0, "itemId", "lantern_draught")
                    : Map.of("kind", "STRIKE", "slot", 0);
            Response round = api.post("/api/battle/action", session, action);
            TestApi.expect(200, round);
            current = round.body();
        }
        return current;
    }
}
