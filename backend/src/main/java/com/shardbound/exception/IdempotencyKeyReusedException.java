package com.shardbound.exception;

/**
 * An idempotency key already used for a different request (P-56). HTTP 422.
 */
public class IdempotencyKeyReusedException extends GameException {

    public IdempotencyKeyReusedException() {
        super("This Idempotency-Key was already used for a different request.");
    }

    @Override
    public String code() {
        return "IDEMPOTENCY_KEY_REUSED";
    }
}
