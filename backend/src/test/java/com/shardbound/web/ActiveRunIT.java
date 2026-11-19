package com.shardbound.web;

import com.shardbound.ShardboundApplication;
import com.shardbound.content.GameContent;
import com.shardbound.support.QuickRouteDriver;
import com.shardbound.support.SharedPostgres;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Durable runs (P-22) with real servers: a game survives a full restart of
 * the application, and two servers sharing one database never overwrite
 * each other's newer state.
 */
class ActiveRunIT {

    private final List<ConfigurableApplicationContext> servers = new ArrayList<>();

    /** Starts a complete, separate server on a random port against the shared database. */
    private TestApi startServer() {
        // Command-line arguments outrank application.yml (default properties would not: server.port
        // there is ${PORT:8080}, and two servers cannot share it).
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ShardboundApplication.class)
                .profiles("test")
                .run("--server.port=0",
                        "--spring.datasource.url=" + SharedPostgres.POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + SharedPostgres.POSTGRES.getUsername(),
                        "--spring.datasource.password=" + SharedPostgres.POSTGRES.getPassword());
        servers.add(context);
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return new TestApi("http://localhost:" + port);
    }

    private GameContent content() {
        return servers.get(0).getBean(GameContent.class);
    }

    @AfterEach
    void stopServers() {
        for (ConfigurableApplicationContext server : servers) {
            server.close();
        }
    }

    /** The view without toasts, which are shown once and not stored. */
    private static JsonNode withoutToasts(JsonNode view) {
        ObjectNode copy = ((ObjectNode) view).deepCopy();
        copy.remove("toasts");
        return copy;
    }

    @Test
    void aGameSurvivesARestartMidFight() {
        TestApi first = startServer();
        int player = first.profile("restart-mid-fight");
        Response created = first.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        String session = created.body().get("sessionId").asString();
        JsonNode inFight = QuickRouteDriver.playUntil(first, content().graph(), session, created.body(),
                view -> "COMBAT".equals(view.get("mode").asString()));
        Response hit = first.post("/api/combat/action", session, Map.of("type", "ATTACK", "target", 0));
        TestApi.expect(200, hit);
        servers.get(0).close();                                            // the deploy

        TestApi second = startServer();
        Response resumed = second.get("/api/game", session);
        TestApi.expect(200, resumed);
        assertEquals(withoutToasts(hit.body()), withoutToasts(resumed.body()));
        assertEquals("COMBAT", resumed.body().get("mode").asString(), "still in the fight: " + inFight.get("node"));
        TestApi.expect(200, second.post("/api/combat/action", session, Map.of("type", "ATTACK", "target", 0)));
    }

    @Test
    void aStaleServerGetsAConflictAndThenCatchesUp() {
        TestApi a = startServer();
        TestApi b = startServer();
        int player = a.profile("two-servers");
        Response created = a.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        String session = created.body().get("sessionId").asString();
        int firstChoice = created.body().get("choices").get(0).get("id").asInt();

        TestApi.expect(200, b.get("/api/game", session));                  // b caches version 1
        TestApi.expect(200, a.post("/api/game/choose", session, Map.of("choiceId", firstChoice)));

        JsonNode bView = b.get("/api/game", session).body();               // still the cached old screen
        int bChoice = bView.get("choices").get(0).get("id").asInt();
        Response conflict = b.post("/api/game/choose", session, Map.of("choiceId", bChoice));
        assertEquals(409, conflict.status());
        assertEquals("RUN_CONFLICT", conflict.code());

        JsonNode fresh = b.get("/api/game", session).body();               // reloaded from the database
        JsonNode aView = a.get("/api/game", session).body();
        assertEquals(withoutToasts(aView), withoutToasts(fresh));
    }
}
