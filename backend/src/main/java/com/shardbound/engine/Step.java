package com.shardbound.engine;

/**
 * One journal entry: the node the player was on and the choice they took.
 * {@code choiceId} is 0 when the choice no longer exists (save_history.choice_id
 * became NULL after a content re-import, D1).
 */
public record Step(String nodeId, int choiceId) {
}
