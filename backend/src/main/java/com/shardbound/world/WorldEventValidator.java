package com.shardbound.world;

import com.shardbound.exception.WorldEventRejectedException;

/**
 * Checks 2–5 of §4.11 on a client-reported position (check 1, the game mode,
 * belongs to the engine). The client walks in tile steps (P-51); the server
 * only accepts events at tile centres the player could really have walked to.
 */
public class WorldEventValidator {

    private final WorldRules rules;

    public WorldEventValidator(WorldRules rules) {
        this.rules = rules;
    }

    /**
     * Checks 2–4: the reported pixel position is the centre of a walkable tile
     * of the current area, reachable from the last validated position, and no further
     * than the player could walk since the last validated event.
     *
     * @param grid the area's collision for this game, obstacles included ({@link AreaMap#collisionWith})
     * @return the reported tile as {@code {x, y}}
     */
    public int[] checkPosition(AreaMap map, CollisionGrid grid, WorldPosition last, long elapsedMillis, int x, int y) {
        int tileX = map.tileOf(x);
        int tileY = map.tileOf(y);
        if (x != map.centre(tileX) || y != map.centre(tileY)) {
            throw new WorldEventRejectedException("The player stands on whole tiles.", last);
        }
        if (!grid.walkable(tileX, tileY)) {
            throw new WorldEventRejectedException("That spot cannot be stood on.", last);
        }
        var path = grid.pathLength(map.tileOf(last.x()), map.tileOf(last.y()), tileX, tileY);
        if (path.isEmpty()) {
            throw new WorldEventRejectedException("That spot cannot be reached from here.", last);
        }
        if (path.getAsInt() > rules.maxTiles(elapsedMillis)) {
            throw new WorldEventRejectedException("You could not have walked that far so quickly.", last);
        }
        return new int[] {tileX, tileY};
    }

    /** Check 5 for exits: the player stands on the exit's rectangle. */
    public void checkOnExit(MapObject exit, int[] tile, WorldPosition last) {
        if (!exit.covers(tile[0], tile[1])) {
            throw new WorldEventRejectedException("You are not at the way out.", last);
        }
    }

    /**
     * Check 5 for interactions: triggers fire when the player stands on them;
     * NPCs and objects are within {@code interactRange} tiles.
     */
    public void checkInRange(MapObject target, int[] tile, WorldPosition last) {
        boolean inRange = target.type() == ObjectType.TRIGGER
                ? target.covers(tile[0], tile[1])
                : target.distanceTo(tile[0], tile[1]) <= rules.interactRange();
        if (!inRange) {
            throw new WorldEventRejectedException("That is too far away.", last);
        }
    }
}
