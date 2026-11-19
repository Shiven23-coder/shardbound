package com.shardbound.web;

import com.shardbound.content.GameContent;
import com.shardbound.support.IntegrationTest;
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
 * with real conditions, a save and a load, achievements, endings and the
 * leaderboard (replaces the old ConsoleRunner --auto-quick check).
 */
class QuickRouteApiIT extends IntegrationTest {

    @Autowired
    private GameContent content;

    @Test
    void theQuickRouteIsPlayableAndRecorded() {
        int player = api.profile("quick-route");
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", true));
        TestApi.expect(200, created);
        String session = created.body().get("sessionId").asString();

        // Play to the square (p03) and save there.
        JsonNode atSquare = QuickRouteDriver.playUntil(api, content.graph(), session, created.body(),
                view -> "p03".equals(view.get("node").get("id").asString()));
        TestApi.expect(204, api.post("/api/saves/1", session, null));
        JsonNode slots = api.get("/api/saves", session).body();
        assertTrue(slots.get(1).get("exists").asBoolean());
        assertEquals("Eldmere Square", slots.get(1).get("areaName").asString());
        assertTrue(slots.get(2).path("areaName").isMissingNode(), "empty slots leave out their null fields");

        // Finish the route.
        JsonNode end = QuickRouteDriver.playUntil(api, content.graph(), session, atSquare, view -> false);
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
        assertEquals("p03", loaded.body().get("node").get("id").asString());
        assertEquals(session, loaded.body().get("sessionId").asString());
        assertEquals("STORY", api.get("/api/game", session).body().get("mode").asString());
    }

    @Test
    void errorsAreProblemDetailsWithAStableCode() {
        Response unknown = api.get("/api/game", "no-such-session");
        assertEquals(404, unknown.status());
        assertEquals("SESSION_NOT_FOUND", unknown.code());
        assertEquals(404, unknown.body().get("status").asInt());

        Response invalid = api.post("/api/profiles", null, Map.of("username", ""));
        assertEquals(400, invalid.status());
        assertEquals("VALIDATION_FAILED", invalid.code());
        assertTrue(invalid.body().get("fields").has("username"));

        Response noPlayer = api.post("/api/game/new", null, Map.of("playerId", 999999, "demoMode", false));
        assertEquals(404, noPlayer.status());
        assertEquals("PLAYER_NOT_FOUND", noPlayer.code());

        // A real session, so the request gets as far as reading its body.
        int player = api.profile("bad-json");
        String session = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false))
                .body().get("sessionId").asString();
        Response badJson = api.post("/api/combat/action", session, Map.of("type", "DANCE"));
        assertEquals(400, badJson.status());
        assertTrue(badJson.code().startsWith("HTTP_400"), badJson.body().toString());
    }

    @Test
    void savingMidFightIsRefused() {
        int player = api.profile("save-mid-fight");
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        String session = created.body().get("sessionId").asString();
        QuickRouteDriver.playUntil(api, content.graph(), session, created.body(),
                view -> "COMBAT".equals(view.get("mode").asString()));
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
