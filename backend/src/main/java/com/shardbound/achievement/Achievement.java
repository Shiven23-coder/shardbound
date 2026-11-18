package com.shardbound.achievement;

/** Immutable achievement definition (title and text are content; the rule is Java). */
public record Achievement(String id, String title, String description, boolean hidden) {
}
