package com.shardbound.save;

import tools.jackson.databind.node.ObjectNode;

/**
 * Turns a save document of version {@link #fromVersion()} into version
 * {@code fromVersion() + 1}. Upgrades work on the JSON tree, so they never
 * depend on Java classes that may change again later.
 */
public interface SaveUpgrade {

    int fromVersion();

    /** Returns the upgraded document; may change {@code document} in place. */
    ObjectNode apply(ObjectNode document);
}
