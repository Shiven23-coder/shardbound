package com.shardbound.world;

/**
 * Walking and world-event constants from content/balance.json (§4.11).
 *
 * @param stepMs          milliseconds per one-tile step: the client walks {@code 1000 / stepMs} tiles per second (P-51)
 * @param speedTolerance  multiplier on the distance the player could have walked
 * @param speedSlackTiles extra tiles that absorb network latency
 * @param interactRange   Chebyshev distance in tiles for interactions
 */
public record WorldRules(int stepMs, double speedTolerance, double speedSlackTiles, int interactRange) {

    /** The walking speed in tiles per second. */
    public double tilesPerSecond() {
        return 1000.0 / stepMs;
    }

    /** The most tiles a player may have walked in {@code elapsedMillis}. */
    public double maxTiles(long elapsedMillis) {
        return tilesPerSecond() * (elapsedMillis / 1000.0) * speedTolerance + speedSlackTiles;
    }
}
