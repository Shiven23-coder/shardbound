package com.shardbound.engine;

/**
 * A request's idempotency key (the client's {@code Idempotency-Key} header)
 * and a hash of the request itself (P-56).
 */
public record ActionKey(String key, String requestHash) { }
