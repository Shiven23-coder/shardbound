package com.shardbound.web;

import com.shardbound.content.GameContent;
import com.shardbound.engine.GameState;
import com.shardbound.engine.LostShards;
import com.shardbound.support.IntegrationTest;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.support.QuickRouteDriver;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import com.shardbound.world.AreaMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Junction Lantern over HTTP (V2_SPEC §4.1, §4.3, §4.4, P-61, P-62): a
 * rest writes the run and slot 0 together, levels are previewed and bought
 * once however often the request is retried, and Lost Shards come back only
 * within a tile.
 */
class LanternApiIT extends IntegrationTest {

    private static final String LANE = "eldmere_cottage_lane";
    private static final String LANTERN = "junction_lantern";
    /** The quick route up to the lane with the Heartshard: the storm night, the ruins and back. */
    private static final int BACK_IN_THE_LANE = 6;

    @Autowired
    private GameContent content;

    @Autowired
    private MutableClock clock;

    @Autowired
    private GameSessions sessions;

    private String session;
    private JsonNode view;

    @BeforeEach
    void backInTheLaneWithTheHeartshard() {
        int player = api.profile("lantern-" + System.nanoTime() % 1_000_000_000);
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        TestApi.expect(200, created);
        session = created.body().get("sessionId").asString();
        view = new QuickRouteDriver(api, content, clock, session).playUntil(created.body(),
                QuickRoute.STEPS.subList(0, BACK_IN_THE_LANE), v -> false);
        assertEquals(LANE, view.get("world").get("areaId").asString());
    }

    private JsonNode post(String path, Object body) {
        Response response = api.post(path, session, body);
        TestApi.expect(200, response);
        return response.body();
    }

    /** Interacts with the Lantern from the tile its rest spawn names, beside it. */
    private JsonNode useLantern() {
        AreaMap lane = content.world().mapOf(LANE);
        var spawn = lane.spawn("junction_rest").orElseThrow();
        clock.advance(Duration.ofMinutes(1));
        return post("/api/world/interact", Map.of("targetId", LANTERN, "x", lane.centre(spawn.x()),
                "y", lane.centre(spawn.y()), "facing", "RIGHT"));
    }

    private JsonNode kindleAndRest() {
        JsonNode kindle = useLantern();
        int warm = kindle.get("dialogue").get("choices").get(0).get("id").asInt();
        post("/api/dialogue/choose", Map.of("choiceId", warm));
        return useLantern();
    }

    @Test
    void aRestOpensTheMenuAndWritesSlotZeroWithTheRun() {
        JsonNode rest = kindleAndRest();
        assertEquals("LANTERN", rest.get("mode").asString());
        assertEquals("Junction Lantern", rest.get("lantern").get("name").asString());
        JsonNode autosave = api.get("/api/saves", session).body().get(0);
        assertTrue(autosave.get("exists").asBoolean(), "slot 0 is written by the rest");
        assertEquals(LANE, autosave.get("areaId").asString());
    }

    @Test
    void aLevelUpIsPreviewedThenBoughtOnceHoweverOftenItIsRetried() {
        kindleAndRest();
        sessions.require(session).stats().addShards(300);
        Map<String, Object> raise = Map.of("raises", Map.of("VIGOR", 1));
        Response preview = api.postKeyed("/api/lantern/levelup/preview", session, raise, null);
        TestApi.expect(200, preview);
        assertEquals(100, preview.body().get("cost").asInt());
        assertEquals(200, preview.body().get("shardsAfter").asInt());

        String key = TestApi.newKey();
        Response first = api.postKeyed("/api/lantern/levelup", session, raise, key);
        TestApi.expect(200, first);
        Response retry = api.postKeyed("/api/lantern/levelup", session, raise, key);
        TestApi.expect(200, retry);
        assertEquals(2, retry.body().get("lantern").get("level").asInt(), "one level, not two");
        assertEquals(200, retry.body().get("lantern").get("shards").asInt());

        Response reused = api.postKeyed("/api/lantern/levelup", session, Map.of("raises", Map.of("MIND", 1)), key);
        assertEquals(422, reused.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.code());
    }

    @Test
    void aLevelUpAwayFromALanternOrBeyondTheShardsIs409() {
        Response away = api.post("/api/lantern/levelup", session, Map.of("raises", Map.of("VIGOR", 1)));
        assertEquals(409, away.status());
        assertEquals("ACTION_NOT_ALLOWED", away.code());
        kindleAndRest();
        Response poor = api.post("/api/lantern/levelup", session, Map.of("raises", Map.of("VIGOR", 5)));
        assertEquals(409, poor.status());
    }

    @Test
    void theLoadoutAndLeavingWork() {
        kindleAndRest();
        java.util.List<String> slots = java.util.Arrays.asList("crystal_pulse", "rending_cut", null, null);
        JsonNode loadout = post("/api/lantern/loadout", Map.of("slots", slots));
        assertEquals("crystal_pulse", loadout.get("lantern").get("loadout").get(0).get("skillId").asString());
        JsonNode left = post("/api/lantern/leave", Map.of());
        assertEquals("WORLD", left.get("mode").asString());
    }

    @Test
    void lostShardsComeBackOnlyWithinATile() {
        kindleAndRest();
        post("/api/lantern/leave", Map.of());
        GameState state = sessions.require(session);
        AreaMap lane = content.world().mapOf(LANE);
        var spawn = lane.spawn("junction_rest").orElseThrow();
        state.setLostShards(new LostShards(LANE, spawn.x() + 3, spawn.y(), 120));

        clock.advance(Duration.ofMinutes(1));
        Response far = api.post("/api/world/recover", session,
                Map.of("x", lane.centre(spawn.x()), "y", lane.centre(spawn.y()), "facing", "RIGHT"));
        assertEquals(409, far.status());
        assertEquals("WORLD_EVENT_REJECTED", far.code());

        clock.advance(Duration.ofMinutes(1));
        JsonNode near = post("/api/world/recover",
                Map.of("x", lane.centre(spawn.x() + 2), "y", lane.centre(spawn.y()), "facing", "RIGHT"));
        assertEquals(120, near.get("hud").get("shards").asInt());
        assertTrue(near.get("world").path("lostShards").isMissingNode() || near.get("world").get("lostShards").isNull());
    }
}
