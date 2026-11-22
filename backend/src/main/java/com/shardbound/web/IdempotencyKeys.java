package com.shardbound.web;

import com.shardbound.engine.ActionKey;
import com.shardbound.exception.IdempotencyKeyRequiredException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Turns a request's {@code Idempotency-Key} header and the request itself
 * into an {@link ActionKey} (P-56): the key, and a SHA-256 hash of the
 * operation and its JSON body, so the same key sent with a different request
 * can be told apart.
 */
public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private IdempotencyKeys() {
    }

    /** @param operation names the endpoint, e.g. {@code "battle/action"}; {@code body} may be null */
    public static ActionKey of(String operation, @Nullable String header, @Nullable Object body) {
        if (header == null || !FORMAT.matcher(header).matches()) {
            throw new IdempotencyKeyRequiredException();
        }
        String request = operation + "\n" + (body == null ? "" : MAPPER.writeValueAsString(body));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8));
            return new ActionKey(header, HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** Checks only that a usable key was sent (for actions that are idempotent by nature). */
    public static void require(@Nullable String header) {
        of("", header, null);
    }
}
