package com.shardbound.api;

import com.shardbound.api.dto.MetaViews.ErrorResponse;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.exception.GameException;
import com.shardbound.exception.InvalidChoiceException;
import com.shardbound.exception.InventoryFullException;
import com.shardbound.exception.LockedChoiceException;
import com.shardbound.exception.SaveFailedException;
import com.shardbound.exception.SaveNotFoundException;
import com.shardbound.persistence.RepositoryException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps exceptions to HTTP status codes and a {@code {error, message}} body
 * (spec §9.1, D27). Controllers never build error responses themselves.
 */
public final class ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {
    }

    public static void register(Javalin app) {
        app.exception(GameException.class, (e, ctx) -> respond(ctx, statusFor(e), e.code(), e.getMessage()));
        app.exception(HttpResponseException.class, (e, ctx) ->
                respond(ctx, e.getStatus(), "HTTP_" + e.getStatus(), e.getMessage()));
        app.exception(IllegalArgumentException.class, (e, ctx) ->
                respond(ctx, 400, "BAD_REQUEST", e.getMessage()));
        app.exception(RepositoryException.class, (e, ctx) -> {
            LOG.error("Database error", e);
            respond(ctx, 500, "DATABASE_ERROR", e.getMessage());
        });
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Unexpected error", e);
            respond(ctx, 500, "INTERNAL_ERROR", e.getClass().getSimpleName() + ": " + e.getMessage());
        });
    }

    /** One place that decides every game exception's status code. */
    static int statusFor(GameException e) {
        if (e instanceof InvalidChoiceException) return 400;
        if (e instanceof LockedChoiceException) return 403;
        if (e instanceof SaveNotFoundException) return 404;
        if (e instanceof ActionNotAllowedException) return 409;
        if (e instanceof InventoryFullException) return 409;
        if (e instanceof SaveFailedException) return 500;
        if (e instanceof ContentValidationException) return 500;
        return 500;
    }

    private static void respond(Context ctx, int status, String error, String message) {
        ctx.status(status).json(new ErrorResponse(error, message));
    }
}
