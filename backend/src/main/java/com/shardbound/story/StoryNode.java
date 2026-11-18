package com.shardbound.story;

/**
 * One page of the story (a vertex of the story graph). Immutable content.
 * encounterId / winNode / loseNode / fleeNode are only set on COMBAT nodes.
 */
public record StoryNode(String id, int chapter, String locationId, NodeType type, String title, String body,
                        String encounterId, String winNode, String loseNode, String fleeNode) {
}
