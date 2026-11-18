package com.shardbound.ds;

/**
 * One outgoing edge in a {@link Graph}: the target vertex and the value
 * carried by the edge.
 */
public record Edge<V, E>(V to, E value) {
}
