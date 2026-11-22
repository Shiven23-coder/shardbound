package com.shardbound.engine;

import com.shardbound.rules.ConditionGroup;

/**
 * One line of the current objective (P-46). The objectives are kept in content
 * order; the first whose conditions hold is the one shown.
 */
public record Objective(String id, String text, ConditionGroup conditions) {
}
