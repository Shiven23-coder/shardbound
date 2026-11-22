package com.shardbound.engine;

import com.shardbound.world.AreaMap;
import com.shardbound.world.CreepSpawn;
import com.shardbound.world.MapObject;
import com.shardbound.world.ObjectType;
import com.shardbound.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * The run's creeps (V2_SPEC §4.2): each spawn's level, rolled from the run's
 * seeded RNG; which ones are down; and which ones the player fled from, with
 * the server time of the escape (P-54). Keys are {@code "area/object"}.
 */
public final class CreepRoster {

    private final Map<String, Integer> levels = new TreeMap<>();
    private final Set<String> defeated = new HashSet<>();
    private final Map<String, Long> fledAt = new HashMap<>();

    public static String key(String areaId, String objectName) {
        return areaId + "/" + objectName;
    }

    /**
     * Rolls a level for every creep spawn in the world, {@code levelMin +
     * nextInt(levelMax − levelMin + 1)}, in key order so a seed always gives
     * the same levels; clears the defeated and fled sets (a respawn).
     */
    public void respawn(World world, Random rng) {
        levels.clear();
        defeated.clear();
        fledAt.clear();
        Map<String, CreepSpawn> spawns = new TreeMap<>();
        for (Map.Entry<String, AreaMap> area : world.maps().entrySet()) {
            for (MapObject o : area.getValue().objectsOf(ObjectType.CREEP_SPAWN)) {
                spawns.put(key(area.getKey(), o.name()), CreepSpawn.of(o));
            }
        }
        spawns.forEach((key, spawn) ->
                levels.put(key, spawn.levelMin() + rng.nextInt(spawn.levelMax() - spawn.levelMin() + 1)));
    }

    public int level(String key) {
        Integer level = levels.get(key);
        if (level == null) {
            throw new IllegalStateException("No level rolled for creep " + key);
        }
        return level;
    }

    public boolean isDefeated(String key) {
        return defeated.contains(key);
    }

    public void markDefeated(String key) {
        defeated.add(key);
        fledAt.remove(key);
    }

    public void markFled(String key, long millis) {
        fledAt.put(key, millis);
    }

    /** True while a creep the player fled from still ignores them (P-54). */
    public boolean ignores(String key, long nowMillis, long ignoreMillis) {
        Long fled = fledAt.get(key);
        return fled != null && nowMillis - fled < ignoreMillis;
    }

    /** True if the player fled from this creep and has not fought it since (the client re-arms its aggro only far away). */
    public boolean wasFled(String key) {
        return fledAt.containsKey(key);
    }

    public Map<String, Integer> levelsView()   { return Collections.unmodifiableMap(levels); }
    public Set<String> defeatedView()          { return Collections.unmodifiableSet(defeated); }
    public Map<String, Long> fledView()        { return Collections.unmodifiableMap(fledAt); }

    /** Puts a stored roster back (loading a save or a run). */
    public void restore(Map<String, Integer> storedLevels, Set<String> storedDefeated, Map<String, Long> storedFled) {
        levels.clear();
        levels.putAll(storedLevels);
        defeated.clear();
        defeated.addAll(storedDefeated);
        fledAt.clear();
        fledAt.putAll(storedFled);
    }
}
