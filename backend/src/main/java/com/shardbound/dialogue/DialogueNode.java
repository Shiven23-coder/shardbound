package com.shardbound.dialogue;

import org.jspecify.annotations.Nullable;

/**
 * One page of a dialogue (a vertex of the dialogue graph). Immutable content.
 * {@code speaker} is the NPC whose portrait is shown, if any.
 * encounterId / winNode / loseNode / fleeNode are only set on COMBAT nodes.
 */
public record DialogueNode(String id, NodeType type, String title, @Nullable String speaker, String body,
                           @Nullable String encounterId, @Nullable String winNode, @Nullable String loseNode,
                           @Nullable String fleeNode) {
}
