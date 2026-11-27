package com.shardbound.web;

import com.shardbound.support.IntegrationTest;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The production profile serves none of the debug routes (P-65, P-67): the rehearsal and the inspector are 404. */
@ActiveProfiles(value = "prod", inheritProfiles = false)
class ProductionProfileIT extends IntegrationTest {

    @Test
    void theDebugRoutesAre404() {
        int player = api.profile("prod-" + System.nanoTime() % 1_000_000_000);
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        TestApi.expect(200, created);
        String session = created.body().get("sessionId").asString();

        assertEquals(404, api.postKeyed("/api/debug/ready-for-the-herald", session, null, TestApi.newKey()).status());
        assertEquals(404, api.get("/api/debug/inspector", session).status());
        assertEquals(404, api.get("/api/debug/weapons", null).status());
    }
}
