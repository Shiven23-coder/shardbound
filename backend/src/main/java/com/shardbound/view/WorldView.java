package com.shardbound.view;

import com.shardbound.world.Facing;

import java.util.List;

/**
 * The walkable world (§8): which map to show, its lighting state, where the
 * player stands (the last validated position, in pixels), the NPCs present
 * and the map objects that are absent for this game right now.
 *
 * @param mapKey        the map file in the client build ({@code maps/<mapKey>.tmj}, P-35)
 * @param lightState    a lighting state of the region in content/art/grades.json
 * @param hiddenObjects names of objects not present now (by showIf/hideIf, or a once-only trigger that fired)
 */
public record WorldView(String areaId, String areaName, String regionId, String mapKey, String lightState,
                        PlayerView player, List<NpcView> npcs, List<CreepView> creeps, List<String> hiddenObjects) {

    public record PlayerView(int x, int y, Facing facing) { }

    /** An NPC standing at a map object: {@code id} is the object name, {@code npcId} picks the sprite. */
    public record NpcView(String id, String npcId, int x, int y) { }

    /**
     * A living creep (V2_SPEC §4.2): {@code id} is the map object name, {@code sprite} an art manifest key,
     * x/y its tile centre in pixels. Coming within {@code aggroRadius} tiles starts a battle; a creep the
     * player fled from ({@code wary}) attacks again only after the player has been
     * {@code aggroRadius + rearmTiles} tiles away (P-54).
     */
    public record CreepView(String id, String template, String name, String sprite, int level, int x, int y,
                            int aggroRadius, boolean wary, int rearmTiles) { }
}
