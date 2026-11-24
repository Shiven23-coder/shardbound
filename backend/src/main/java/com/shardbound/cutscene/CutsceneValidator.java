package com.shardbound.cutscene;

import com.shardbound.world.AreaMap;
import com.shardbound.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the cutscenes at startup (P-68): each plays in a built area; its actors stand on tiles of that map;
 * every step names an actor it has (or the player), a known speaker, a light state of the area's region and the
 * fields its kind needs; and {@code then} returns to the world or leads to a known boss. Errors stop startup.
 */
public final class CutsceneValidator {

    private static final String PLAYER = "player";

    private final World world;
    private final Set<String> npcs;
    private final Set<String> bosses;

    /**
     * @param npcs   every NPC id (a {@code say} speaker may be one)
     * @param bosses every boss id (a cutscene may lead into one)
     */
    public CutsceneValidator(World world, Set<String> npcs, Set<String> bosses) {
        this.world = world;
        this.npcs = npcs;
        this.bosses = bosses;
    }

    public List<String> validate(Map<String, Cutscene> cutscenes) {
        List<String> errors = new ArrayList<>();
        for (Cutscene c : cutscenes.values()) {
            check(c, errors);
        }
        return errors;
    }

    private void check(Cutscene c, List<String> errors) {
        String where = "cutscene " + c.id();
        World.Area area = world.areas().get(c.area());
        AreaMap map = area == null ? null : world.maps().get(c.area());
        if (map == null) {
            errors.add(where + ": plays in unknown or unbuilt area " + c.area());
            return;
        }
        Set<String> actors = new HashSet<>();
        for (Cutscene.Actor actor : c.actors()) {
            if (!actors.add(actor.id()) || PLAYER.equals(actor.id())) {
                errors.add(where + ": actor id " + actor.id() + " is used twice or reserved");
            }
            if (!onMap(map, actor.x(), actor.y())) {
                errors.add(where + ": actor " + actor.id() + " stands off the map");
            }
        }
        Set<String> lightStates = new HashSet<>();
        world.regions().get(area.region()).lightStates().forEach(s -> lightStates.add(s.id()));
        for (int i = 0; i < c.steps().size(); i++) {
            checkStep(where + " step " + (i + 1), c.steps().get(i), map, actors, lightStates, errors);
        }
        String boss = c.thenBoss();
        if (!Cutscene.THEN_WORLD.equals(c.then()) && (boss == null || !bosses.contains(boss))) {
            errors.add(where + ": then must be \"world\" or \"battle:<boss>\", not " + c.then());
        }
    }

    private void checkStep(String where, Cutscene.Step step, AreaMap map, Set<String> actors, Set<String> lightStates,
                           List<String> errors) {
        boolean needsActor = switch (step.kind()) {
            case MOVE, FACE, VANISH -> true;
            default -> false;
        };
        if (needsActor && step.actor() == null) {
            errors.add(where + ": " + step.kind() + " needs an actor");
        }
        if (step.actor() != null && !PLAYER.equals(step.actor()) && !actors.contains(step.actor())) {
            errors.add(where + ": unknown actor " + step.actor());
        }
        switch (step.kind()) {
            case SAY -> {
                if (step.text() == null) {
                    errors.add(where + ": say needs text");
                }
                if (step.speaker() != null && !npcs.contains(step.speaker()) && !actors.contains(step.speaker())) {
                    errors.add(where + ": unknown speaker " + step.speaker());
                }
            }
            case MOVE -> {
                if (step.path().isEmpty()) {
                    errors.add(where + ": move needs a path");
                }
                for (Cutscene.Tile tile : step.path()) {
                    if (!onMap(map, tile.x(), tile.y())) {
                        errors.add(where + ": the path leaves the map at " + tile.x() + "," + tile.y());
                    }
                }
            }
            case CAMERA -> {
                if (step.camera() == null) {
                    errors.add(where + ": camera needs pan, zoom, shake or follow");
                }
            }
            case LIGHT -> {
                if (step.state() == null || !lightStates.contains(step.state())) {
                    errors.add(where + ": unknown light state " + step.state());
                }
            }
            case MUSIC -> {
                if (step.track() == null) {
                    errors.add(where + ": music needs a track");
                }
            }
            case SOUND -> {
                if (step.effect() == null) {
                    errors.add(where + ": sound needs an effect");
                }
            }
            default -> { }
        }
    }

    private static boolean onMap(AreaMap map, int x, int y) {
        return x >= 0 && y >= 0 && x < map.width() && y < map.height();
    }
}
