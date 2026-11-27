package com.shardbound.web;

import com.shardbound.support.IntegrationTest;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The e2e profile's Herald rehearsal over HTTP (P-67): keyed, and it leaves the player at the Ruins Lantern. */
@ActiveProfiles({"test", "e2e"})
class RehearsalApiIT extends IntegrationTest {

    @Test
    void theRehearsalPutsTheRunAtTheRuinsLanternAndIsKeyed() {
        int player = api.profile("rehearsal-" + System.nanoTime() % 1_000_000_000);
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        TestApi.expect(200, created);
        String session = created.body().get("sessionId").asString();

        String key = TestApi.newKey();
        Response ready = api.postKeyed("/api/debug/ready-for-the-herald", session, null, key);
        TestApi.expect(200, ready);
        assertEquals("WORLD", ready.body().get("mode").asString());
        assertEquals("eldmere_old_ruins", ready.body().get("world").get("areaId").asString());
        assertEquals(4, ready.body().get("hud").get("level").asInt());

        Response retry = api.postKeyed("/api/debug/ready-for-the-herald", session, null, key);
        TestApi.expect(200, retry);
        assertEquals(4, retry.body().get("hud").get("level").asInt(), "a retried rehearsal is applied once");
    }
}
