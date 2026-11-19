package com.shardbound.web;

import com.shardbound.persistence.ActiveRunRepository;
import com.shardbound.persistence.MetaRepository;
import com.shardbound.persistence.PlayerRepository;
import com.shardbound.persistence.SaveRepository;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes the API contract to {@code api/openapi.json} (P-07). The file is
 * committed; CI regenerates it and fails if it differs, so the frontend's
 * generated client can never drift from the server.
 *
 * <p>Only the web layer matters here, so the database is switched off and the
 * repositories are mocks: the contract builds without Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ActiveProfiles("test")
class OpenApiContractTest {

    static final Path CONTRACT = Path.of("../api/openapi.json");

    @MockitoBean
    PlayerRepository players;
    @MockitoBean
    MetaRepository meta;
    @MockitoBean
    SaveRepository saves;
    @MockitoBean
    ActiveRunRepository runs;

    @LocalServerPort
    int port;

    @Test
    void exportTheContract() throws IOException {
        Response response = new TestApi("http://localhost:" + port).get("/v3/api-docs", null);
        TestApi.expect(200, response);
        JsonNode spec = response.body();

        assertTrue(spec.get("paths").has("/api/game/new"));
        JsonNode gameView = spec.get("components").get("schemas").get("GameView");
        assertTrue(contains(gameView.get("required"), "hud"), "non-null record fields are required");
        assertFalse(contains(gameView.get("required"), "combat"), "@Nullable fields are optional");
        assertEquals("apiKey", spec.get("components").get("securitySchemes").get("session").get("type").asString());

        JsonMapper pretty = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.createDirectories(CONTRACT.getParent());
        Files.writeString(CONTRACT, pretty.writeValueAsString(spec).replace("\r\n", "\n") + "\n");
    }

    private static boolean contains(JsonNode array, String value) {
        if (array == null) {
            return false;
        }
        for (JsonNode item : array) {
            if (item.asString().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
