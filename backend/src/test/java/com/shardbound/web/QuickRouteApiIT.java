package com.shardbound.web;

import com.shardbound.content.GameContent;
import com.shardbound.support.IntegrationTest;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.support.QuickRouteDriver;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole stack end to end over HTTP: profile, new game, the quick route
 * through the world with real conditions and server-validated world events,
 * a save and a load, achievements, endings and the leaderboard.
 */
class QuickRouteApiIT extends IntegrationTest {

    @Autowired
    private GameContent content;

    @Autowired
    private MutableClock clock;

    private String newGame(String username, boolean demoMode) {
        int player = api.profile(username);
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", demoMode));
        TestApi.expect(200, created);
        return created.body().get("sessionId").asString();
    }

    @Test
    void theQuickRouteIsPlayableAndRecorded() {
        int player = api.profile("quick-route");
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", true));
        TestApi.expect(200, created);
        String session = created.body().get("sessionId").asString();
        QuickRouteDriver driver = new QuickRouteDriver(api, content, clock, session);

        // Play to the Square at dawn and save there.
        JsonNode atSquare = driver.playUntil(created.body(), QuickRoute.STEPS.subList(0, QuickRoute.AT_SQUARE),
                view -> false);
        assertEquals("WORLD", atSquare.get("mode").asString());
        assertEquals("eldmere_square", atSquare.get("world").get("areaId").asString());
        TestApi.expect(204, api.post("/api/saves/1", session, null));
        JsonNode slots = api.get("/api/saves", session).body();
        assertTrue(slots.get(1).get("exists").asBoolean());
        assertEquals("Eldmere Square", slots.get(1).get("areaName").asString());
        assertTrue(slots.get(2).path("areaName").isMissingNode(), "empty slots leave out their null fields");

        // Finish the route.
        JsonNode end = driver.playUntil(atSquare, QuickRoute.STEPS.subList(QuickRoute.AT_SQUARE, QuickRoute.STEPS.size()),
                view -> false);
        assertEquals("ENDED", end.get("mode").asString());
        assertEquals("END_QUICK", end.get("ending").get("id").asString());

        // The ending and its achievement are stored for this player.
        JsonNode achievements = api.get("/api/achievements", session).body();
        assertTrue(unlocked(achievements, "SHORT_TALE"));
        JsonNode endings = api.get("/api/endings?playerId=" + player, null).body();
        assertTrue(reached(endings, "END_QUICK"));
        JsonNode leaderboard = api.get("/api/leaderboard", null).body();
        assertTrue(leaderboard.toString().contains("quick-route"));

        // Loading the slot brings the square back into the same session.
        Response loaded = api.post("/api/saves/1/load", session, null);
        TestApi.expect(200, loaded);
        assertEquals("WORLD", loaded.body().get("mode").asString());
        assertEquals(atSquare.get("world"), loaded.body().get("world"));
        assertEquals(session, loaded.body().get("sessionId").asString());
    }

    @Test
    void aRejectedWorldEventReportsTheLastValidatedPosition() {
        String session = newGame("teleporter", false);
        JsonNode view = new QuickRouteDriver(api, content, clock, session).playUntil(
                api.get("/api/game", session).body(), QuickRoute.STEPS.subList(0, 1), v -> false);
        JsonNode player = view.get("world").get("player");

        // Straight to the ruins exit with no time passed: faster than anyone can walk.
        Response teleport = api.post("/api/world/exit", session,
                Map.of("exitId", "to_ruins", "x", 19 * 16 + 8, "y", 8, "facing", "UP"));
        assertEquals(409, teleport.status());
        assertEquals("WORLD_EVENT_REJECTED", teleport.code());
        JsonNode last = teleport.body().get("lastPosition");
        assertEquals("eldmere_cottage_lane", last.get("areaId").asString());
        assertEquals(player.get("x").asInt(), last.get("x").asInt());
        assertEquals(player.get("y").asInt(), last.get("y").asInt());
    }

    @Test
    void errorsAreProblemDetailsWithAStableCode() {
        Response unknown = api.get("/api/game", "no-such-session");
        assertEquals(404, unknown.status());
        assertEquals("RUN_NOT_FOUND", unknown.code());
        assertEquals(404, unknown.body().get("status").asInt());

        Response invalid = api.post("/api/profiles", null, Map.of("username", ""));
        assertEquals(400, invalid.status());
        assertEquals("VALIDATION_FAILED", invalid.code());
        assertTrue(invalid.body().get("fields").has("username"));

        Response noPlayer = api.post("/api/game/new", null, Map.of("playerId", 999999, "demoMode", false));
        assertEquals(404, noPlayer.status());
        assertEquals("PLAYER_NOT_FOUND", noPlayer.code());

        // A real session, so the request gets as far as reading its body.
        String session = newGame("bad-json", false);
        Response badJson = api.post("/api/battle/action", session, Map.of("kind", "DANCE"));
        assertEquals(400, badJson.status());
        assertTrue(badJson.code().startsWith("HTTP_400"), badJson.body().toString());

        Response noFacing = api.post("/api/world/exit", session, Map.of("exitId", "to_ruins", "x", 8, "y", 8));
        assertEquals(400, noFacing.status());
        assertEquals("VALIDATION_FAILED", noFacing.code());
    }

    @Test
    void savingMidFightIsRefused() {
        String session = newGame("save-mid-fight", false);
        new QuickRouteDriver(api, content, clock, session).playUntil(api.get("/api/game", session).body(),
                QuickRoute.STEPS, view -> "BATTLE".equals(view.get("mode").asString()));
        Response save = api.post("/api/saves/1", session, null);
        assertEquals(409, save.status());
        assertEquals("ACTION_NOT_ALLOWED", save.code());
    }

    private static boolean unlocked(JsonNode achievements, String id) {
        for (JsonNode a : achievements) {
            if (a.get("id").asString().equals(id)) {
                return a.get("unlocked").asBoolean();
            }
        }
        return false;
    }

    private static boolean reached(JsonNode endings, String id) {
        for (JsonNode e : endings) {
            if (e.get("id").asString().equals(id)) {
                return e.get("reached").asBoolean();
            }
        }
        return false;
    }
}
