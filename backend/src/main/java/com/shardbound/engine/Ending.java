package com.shardbound.engine;

import com.shardbound.rules.ConditionGroup;

/**
 * Immutable ending definition. By convention nodeId equals id.
 * viaResolver = false (END_QUICK) means the resolver never picks it (D11).
 */
public record Ending(String id, String nodeId, String title, int priority, boolean viaResolver,
                     ConditionGroup conditions) {
}
