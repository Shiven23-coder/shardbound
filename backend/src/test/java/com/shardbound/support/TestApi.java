package com.shardbound.support;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

/**
 * A tiny JSON client for integration tests: real HTTP, real headers, no Spring
 * magic. Every POST and DELETE carries a fresh Idempotency-Key (P-56) unless a
 * test passes its own (or none) to the keyed variants.
 */
public class TestApi {

    public static final String SESSION = "X-Session";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Status and parsed body (NullNode when the body is empty). */
    public record Response(int status, JsonNode body) {

        public String code() {
            return body.path("code").asString();
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    public TestApi(String base) {
        this.base = base;
    }

    public Response get(String path, @Nullable String session) {
        return send(request(path, session).GET().build());
    }

    public Response post(String path, @Nullable String session, @Nullable Object body) {
        return postKeyed(path, session, body, newKey());
    }

    /** A POST with this idempotency key, or without the header when it is null. */
    public Response postKeyed(String path, @Nullable String session, @Nullable Object body, @Nullable String key) {
        String json = body == null ? "" : JSON.writeValueAsString(body);
        return send(keyed(request(path, session), key).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
    }

    public Response delete(String path, @Nullable String session) {
        return send(keyed(request(path, session), newKey()).DELETE().build());
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static HttpRequest.Builder keyed(HttpRequest.Builder builder, @Nullable String key) {
        return key == null ? builder : builder.header(IDEMPOTENCY_KEY, key);
    }

    /** Creates (or finds) a profile and returns its player id. */
    public int profile(String username) {
        Response response = post("/api/profiles", null, Map.of("username", username));
        expect(200, response);
        return response.body().get("playerId").asInt();
    }

    public static void expect(int status, Response response) {
        if (response.status() != status) {
            throw new AssertionError("Expected HTTP " + status + " but got " + response.status() + ": "
                    + response.body());
        }
    }

    private HttpRequest.Builder request(String path, @Nullable String session) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).header("Accept", "application/json");
        if (session != null) {
            builder.header(SESSION, session);
        }
        return builder;
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body().isBlank() ? NullNode.getInstance() : JSON.readTree(response.body());
            return new Response(response.statusCode(), body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
