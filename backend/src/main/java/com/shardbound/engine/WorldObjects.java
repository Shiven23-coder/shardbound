package com.shardbound.engine;

import com.shardbound.world.AreaMap;
import com.shardbound.world.CollisionGrid;
import com.shardbound.world.MapObject;
import com.shardbound.world.ObjectType;

/**
 * Whether a map object is present for this game right now: {@code showIf}
 * needs its flag set, {@code hideIf} needs it unset, and a {@code once}
 * trigger disappears after it fired.
 */
final class WorldObjects {

    private WorldObjects() {
    }

    static boolean active(GameState s, String areaId, MapObject o) {
        String showIf = o.property("showIf");
        String hideIf = o.property("hideIf");
        if (showIf != null && s.flag(showIf) == 0) {
            return false;
        }
        if (hideIf != null && s.flag(hideIf) != 0) {
            return false;
        }
        return !(o.type() == ObjectType.TRIGGER && o.flag("once") && s.flag(onceFlag(areaId, o)) != 0);
    }

    /** Where the player can walk in an area for this game: active obstacles block (P-45). */
    static CollisionGrid collision(GameState s, String areaId, AreaMap map) {
        return map.collisionWith(o -> active(s, areaId, o));
    }

    /** The engine-reserved flag (D15) recording that a once-only trigger fired. */
    static String onceFlag(String areaId, MapObject trigger) {
        return "_once_" + areaId + "_" + trigger.name();
    }
}
