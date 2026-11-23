package com.shardbound.save;

/** The save document format this server writes. */
public final class SaveFormat {

    /**
     * Bump this together with a new {@link SaveUpgrade} whenever the shape of
     * {@link SaveSnapshot} changes in a way old documents cannot be read as.
     */
    public static final int CURRENT_VERSION = 4;

    private SaveFormat() {
    }
}
