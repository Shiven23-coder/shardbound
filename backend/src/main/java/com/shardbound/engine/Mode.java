package com.shardbound.engine;

/**
 * What the player is doing right now; decides which requests are allowed. LANTERN: the Lantern menu is open
 * (P-61). CUTSCENE: a cutscene is playing until the client finishes or skips it (P-68).
 */
public enum Mode {
    WORLD, DIALOGUE, BATTLE, LANTERN, CUTSCENE, ENDED
}
