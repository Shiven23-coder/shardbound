package com.shardbound.web;

import com.shardbound.content.GameContent;
import com.shardbound.support.IntegrationTest;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.support.QuickRoute.Choose;
import com.shardbound.support.QuickRoute.Exit;
import com.shardbound.support.QuickRoute.Interact;
import com.shardbound.support.QuickRouteDriver;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The Ashen Herald over HTTP (P-67, P-68): stepping onto the Rise plays the intro; finishing it is keyed (a
 * retry is the same answer, a reused key is 422) and starts a fight that cannot be fled.
 */
class HeraldApiIT extends IntegrationTest {

    /** The elder's path to "The Ash Falls", then up through the ruins onto the Rise and onto the trigger. */
    private static final List<QuickRoute.Step> TO_THE_HERALD = List.of(
            new Choose("Ignore"), new Exit("to_ruins"), new Interact("crater_crystal"), new Choose("Take the crystal"),
            new Choose("Walk on"), new Exit("to_lane"), new Exit("to_square"), new Interact("maren"),
            new Choose("Follow her inside"), new Choose("Promise"), new Choose("Go to the ruins"), new Exit("to_lane"),
            new Exit("to_ruins"), new Exit("arena_way"), new Interact("herald_trigger"));

    @Autowired
    private GameContent content;

    @Autowired
    private MutableClock clock;

    private String session;
    private JsonNode intro;

    @BeforeEach
    void onTheRise() {
        int player = api.profile("herald-" + System.nanoTime() % 1_000_000_000);
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        TestApi.expect(200, created);
        session = created.body().get("sessionId").asString();
        intro = new QuickRouteDriver(api, content, clock, session).playUntil(created.body(), TO_THE_HERALD, v -> false);
    }

    @Test
    void theIntroPlaysAndItsFinishIsKeyed() {
        assertEquals("CUTSCENE", intro.get("mode").asString());
        assertEquals("herald_intro", intro.get("cutscene").get("id").asString());

        String key = TestApi.newKey();
        Response finished = api.postKeyed("/api/cutscene/finish", session, Map.of("cutsceneId", "herald_intro"), key);
        TestApi.expect(200, finished);
        JsonNode battle = finished.body().get("battle");
        assertEquals("BATTLE", finished.body().get("mode").asString());
        assertEquals("Ashen Herald", battle.get("boss").get("name").asString());
        assertFalse(battle.get("menu").get("canFlee").asBoolean());

        Response retry = api.postKeyed("/api/cutscene/finish", session, Map.of("cutsceneId", "herald_intro"), key);
        TestApi.expect(200, retry);
        assertEquals("BATTLE", retry.body().get("mode").asString(), "a retried finish is applied once");

        Response reused = api.postKeyed("/api/cutscene/finish", session, Map.of("cutsceneId", "herald_epilogue"), key);
        assertEquals(422, reused.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.code());
    }

    @Test
    void finishingWhenNoCutsceneIsPlayingIs409() {
        TestApi.expect(200, api.postKeyed("/api/cutscene/finish", session, Map.of("cutsceneId", "herald_intro"),
                TestApi.newKey()));
        Response again = api.postKeyed("/api/cutscene/finish", session, Map.of("cutsceneId", "herald_intro"),
                TestApi.newKey());
        assertEquals(409, again.status());
        assertEquals("ACTION_NOT_ALLOWED", again.code());
    }
}
