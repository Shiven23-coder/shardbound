package com.shardbound.exception;

/**
 * A state-changing request without a usable {@code Idempotency-Key} header (P-56). HTTP 400.
 */
public class IdempotencyKeyRequiredException extends GameException {

    public IdempotencyKeyRequiredException() {
        super("Send an Idempotency-Key header (a UUID) with every action.");
    }

    @Override
    public String code() {
        return "IDEMPOTENCY_KEY_REQUIRED";
    }
}
