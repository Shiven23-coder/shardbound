package com.shardbound.web;

import com.shardbound.config.OpenApiConfig;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.GameException;
import com.shardbound.exception.InvalidChoiceException;
import com.shardbound.exception.InventoryFullException;
import com.shardbound.exception.LockedChoiceException;
import com.shardbound.exception.PlayerNotFoundException;
import com.shardbound.exception.RunConflictException;
import com.shardbound.exception.SaveIncompatibleException;
import com.shardbound.exception.SaveNotFoundException;
import com.shardbound.exception.SessionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the API as an RFC 9457 {@link ProblemDetail} with a
 * stable {@code code} property the client can switch on (P-07). Internal
 * details never reach the client; they go to the log.
 *
 * <p>When a request fails in a way that may leave the cached game ahead of
 * (or behind) the database, the cached copy is dropped so the next request
 * reloads it from {@code active_run}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final GameSessions sessions;

    public ApiExceptionHandler(GameSessions sessions) {
        this.sessions = sessions;
    }

    @ExceptionHandler(GameException.class)
    ResponseEntity<ProblemDetail> gameException(GameException e, HttpServletRequest request) {
        if (e instanceof RunConflictException || e instanceof SaveIncompatibleException) {
            sessions.evict(request.getHeader(OpenApiConfig.SESSION_HEADER));
        }
        HttpStatus status = statusFor(e);
        if (status.is5xxServerError()) {
            LOG.error("Game error", e);
        }
        return problem(status, e.code(), e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException e) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        LOG.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), e);
        sessions.evict(request.getHeader(OpenApiConfig.SESSION_HEADER));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong on our side.");
    }

    /** Bean validation of request bodies: 400 with one message per field. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is not valid.");
        body.setProperty("code", "VALIDATION_FAILED");
        body.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    /** Spring's own errors (unreadable JSON, wrong method, …) get a code too. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            problem.setProperty("code", "HTTP_" + status.value());
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    /** The single place that decides each game exception's status. */
    static HttpStatus statusFor(GameException e) {
        return switch (e) {
            case InvalidChoiceException ignored -> HttpStatus.BAD_REQUEST;
            case LockedChoiceException ignored -> HttpStatus.FORBIDDEN;
            case SaveNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case SessionNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case PlayerNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ActionNotAllowedException ignored -> HttpStatus.CONFLICT;
            case InventoryFullException ignored -> HttpStatus.CONFLICT;
            case RunConflictException ignored -> HttpStatus.CONFLICT;
            case SaveIncompatibleException ignored -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("code", code);
        return ResponseEntity.status(status).body(body);
    }
}
