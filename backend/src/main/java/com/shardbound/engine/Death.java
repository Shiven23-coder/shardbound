package com.shardbound.engine;

import com.shardbound.world.AreaMap;
import com.shardbound.world.Lantern;
import com.shardbound.world.World;
import com.shardbound.world.WorldPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Losing a creep battle (V2_SPEC §4.4, P-62), the one place that decides what
 * a death costs:
 * <ol>
 *   <li>Lost Shards still on the map are destroyed;</li>
 *   <li>held Shards drop as Lost Shards at the tile where the battle started;</li>
 *   <li>the player wakes at the last Lantern rested at, or at the region's
 *       respawn point before any, with full HP and FP and a full Lantern
 *       Draught, and the creeps respawn.</li>
 * </ol>
 * A deaths counter (the {@code deaths} flag) lets dialogue react. What
 * happened is returned as lines for the end of the battle's text, read before
 * Continue (a toast would be hidden behind the battle).
 */
final class Death {

    static final String DEATHS_FLAG = "deaths";

    private final World world;

    Death(World world) {
        this.world = world;
    }

    List<String> apply(GameState s, long nowMillis) {
        List<String> lines = new ArrayList<>();
        WorldPosition fell = s.position();
        AreaMap fellMap = world.mapOf(fell.areaId());
        if (s.lostShards() != null) {
            lines.add("The Shards you lost before fade into the dark.");
        }
        int held = s.stats().takeAllShards();
        s.setLostShards(held == 0 ? null
                : new LostShards(fell.areaId(), fellMap.tileOf(fell.x()), fellMap.tileOf(fell.y()), held));
        s.setFlag(DEATHS_FLAG, s.flag(DEATHS_FLAG) + 1);

        Lantern lantern = s.lastLantern() == null ? null : world.lantern(s.lastLantern()).orElse(null);
        String area;
        String spawn;
        if (lantern != null) {
            area = lantern.areaId();
            spawn = lantern.spawn();
        } else {
            World.Place place = world.regions().get(world.area(fell.areaId()).region()).respawn();
            area = place.area();
            spawn = place.spawn();
        }
        AreaMap map = world.mapOf(area);
        s.moveTo(WorldPosition.at(area, map, map.spawn(spawn).orElseThrow()), nowMillis);
        s.stats().restoreAll();
        s.refillFlasks();
        s.creeps().respawn(world, s.rng());
        lines.add(lantern != null ? "You wake beside the " + lantern.name() + "." : "You wake at home, bruised.");
        return lines;
    }
}
