package com.shardbound.support;

import com.shardbound.world.AreaMap;
import com.shardbound.world.CollisionGrid;
import com.shardbound.world.MapObject;
import com.shardbound.world.ObjectType;

import java.util.BitSet;
import java.util.List;

/**
 * The quick (merchant) route through the world as the steps a player takes:
 * dialogue choices by label, exits and interactions by object name. Fights
 * on the way are won by attacking. Shared by the engine tests and the HTTP
 * tests so both play exactly the same route.
 */
public final class QuickRoute {

    public static final long SEED = 42L;

    public sealed interface Step permits Choose, Exit, Interact { }

    /** Take the unlocked dialogue choice with this label. */
    public record Choose(String label) implements Step { }

    /** Walk onto this exit of the current area. */
    public record Exit(String exitId) implements Step { }

    /** Walk up to this NPC, object or trigger and use it. */
    public record Interact(String targetId) implements Step { }

    public static final List<Step> STEPS = List.of(
            new Choose("Help Corvin lash down his wagon before the wind takes it"),
            new Exit("to_ruins"),
            new Interact("crater_crystal"),
            new Choose("Take the crystal in your bare hands"),
            new Choose("Walk on"),
            new Exit("to_lane"),
            new Exit("to_square"),
            new Interact("square_dawn"),
            new Choose("Look around the square"),
            new Interact("corvin_stall"),
            new Choose("Accept the three hundred gold"),
            new Choose("Follow him"),
            new Interact("alley_mouth"),
            new Choose("Step into the lantern light"),
            new Choose("Take the hatchet"),
            new Choose("Take the crystal back by force"),
            new Choose("Run for the forest road"));

    /** The index of the step that reaches the Square at dawn (a good place to save). */
    public static final int AT_SQUARE = STEPS.indexOf(new Choose("Look around the square")) + 1;

    private QuickRoute() {
    }

    /**
     * A pixel position (tile centre) from which {@code target} can be used,
     * reachable on foot from the tile {@code (fromX, fromY)} on {@code grid} (the
     * area's collision for this game): on the object for exits and triggers,
     * within {@code range} tiles for NPCs and objects.
     */
    public static int[] standingSpot(AreaMap map, CollisionGrid grid, MapObject target, int fromX, int fromY, int range) {
        BitSet reachable = grid.reachableFrom(fromX, fromY);
        boolean onTop = target.type() == ObjectType.EXIT || target.type() == ObjectType.TRIGGER;
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                boolean usable = onTop ? target.covers(x, y) : target.distanceTo(x, y) <= range;
                if (usable && reachable.get(y * map.width() + x)) {
                    return new int[] {map.centre(x), map.centre(y)};
                }
            }
        }
        throw new AssertionError("No reachable spot to use " + target.name() + " in " + map.key());
    }
}
