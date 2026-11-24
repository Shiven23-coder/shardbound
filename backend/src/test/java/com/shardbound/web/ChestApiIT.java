package com.shardbound.web;

import com.shardbound.content.GameContent;
import com.shardbound.support.IntegrationTest;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.support.QuickRouteDriver;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import com.shardbound.world.AreaMap;
import com.shardbound.world.MapObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ruins chest over HTTP (P-65): opened once however often the request is retried, a key reused for
 * another body refused, an open chest refused, and the opened state kept by a save and a load.
 */
class ChestApiIT extends IntegrationTest {

    private static final String RUINS = "eldmere_old_ruins";
    private static final String CHEST = "ruins_chest";
    /** The quick route to the Old Ruins at dawn: the storm night, the crater crystal, "Walk on". */
    private static final int RUINS_AT_DAWN = QuickRoute.STEPS.indexOf(new QuickRoute.Choose("Walk on")) + 1;

    @Autowired
    private GameContent content;

    @Autowired
    private MutableClock clock;

    private String session;
    /** Where the player stood when the route reached the ruins: every open starts from there. */
    private JsonNode start;

    @BeforeEach
    void inTheRuinsAtDawn() {
        int player = api.profile("chest-" + System.nanoTime() % 1_000_000_000);
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        TestApi.expect(200, created);
        session = created.body().get("sessionId").asString();
        JsonNode view = new QuickRouteDriver(api, content, clock, session).playUntil(created.body(),
                QuickRoute.STEPS.subList(0, RUINS_AT_DAWN), v -> false);
        assertEquals(RUINS, view.get("world").get("areaId").asString());
        start = view.get("world").get("player");
    }

    /** Opens the chest from a tile in reach of it, walked to from where the route left the player. */
    private Response open(String key, String facing) {
        AreaMap ruins = content.world().mapOf(RUINS);
        MapObject chest = ruins.object(CHEST).orElseThrow();
        int[] spot = QuickRoute.standingSpot(ruins, ruins.collisionWith(o -> true), chest,
                ruins.tileOf(start.get("x").asInt()), ruins.tileOf(start.get("y").asInt()),
                content.worldRules().interactRange());
        clock.advance(Duration.ofMinutes(1));
        return api.postKeyed("/api/world/interact", session,
                Map.of("targetId", CHEST, "x", spot[0], "y", spot[1], "facing", facing), key);
    }

    private static JsonNode chest(JsonNode view) {
        for (JsonNode c : view.get("world").get("chests")) {
            if (c.get("id").asString().equals(CHEST)) {
                return c;
            }
        }
        throw new AssertionError("no " + CHEST + " in " + view.get("world"));
    }

    private static int count(JsonNode view, String itemId) {
        for (JsonNode item : view.get("inventory").get("items")) {
            if (item.get("id").asString().equals(itemId)) {
                return item.get("qty").asInt();
            }
        }
        return 0;
    }

    @Test
    void aChestOpensOnceHoweverOftenTheRequestIsRetried() {
        String key = TestApi.newKey();
        Response first = open(key, "UP");
        TestApi.expect(200, first);
        assertTrue(chest(first.body()).get("open").asBoolean());
        JsonNode loot = first.body().get("toasts").get(0);
        assertEquals("LOOT", loot.get("kind").asString());
        assertEquals("E", loot.get("tier").asString());

        Response retry = open(key, "UP");
        TestApi.expect(200, retry);
        assertEquals(1, count(retry.body(), "woodcutter_hatchet"), "one hatchet, not two");
        assertEquals(0, retry.body().get("toasts").size(), "the retry is the same answer, not a second opening");

        Response reused = open(key, "LEFT");
        assertEquals(422, reused.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.code());

        Response again = open(TestApi.newKey(), "UP");
        assertEquals(409, again.status(), "an open chest is empty");
    }

    @Test
    void anOpenedChestStaysOpenThroughASaveAndALoad() {
        TestApi.expect(200, open(TestApi.newKey(), "UP"));
        TestApi.expect(204, api.postKeyed("/api/saves/1", session, null, TestApi.newKey()));
        Response loaded = api.postKeyed("/api/saves/1/load", session, null, TestApi.newKey());
        TestApi.expect(200, loaded);
        assertTrue(chest(loaded.body()).get("open").asBoolean());
        assertEquals(1, count(loaded.body(), "woodcutter_hatchet"));
    }
}
