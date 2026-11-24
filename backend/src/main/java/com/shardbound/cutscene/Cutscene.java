package com.shardbound.cutscene;

import com.shardbound.rules.ConditionGroup;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A data-driven cutscene from {@code content/cutscenes/<id>.json} (P-68): actors drawn with map sprites, steps
 * the client plays in order, and what follows ({@code then}: {@code "world"} or {@code "battle:<bossId>"}). The
 * server decides when one plays and what follows; the client only stages it.
 */
public record Cutscene(String id, String area, List<Actor> actors, List<Step> steps, String then) {

    public static final String THEN_WORLD = "world";
    public static final String THEN_BATTLE = "battle:";

    public Cutscene {
        actors = List.copyOf(actors);
        steps = List.copyOf(steps);
    }

    /** The boss the cutscene leads into, or null when it returns to the world. */
    public @Nullable String thenBoss() {
        return then.startsWith(THEN_BATTLE) ? then.substring(THEN_BATTLE.length()) : null;
    }

    /** A sprite placed for the cutscene at a tile, facing a way; steps name it by {@code id}; {@code name} titles its lines. */
    public record Actor(String id, String sprite, int x, int y, String facing, @Nullable String name) { }

    public record Tile(int x, int y) { }

    /** A {@code say} line used instead of the step's own text when its conditions hold (the first that holds). */
    public record Variant(ConditionGroup conditions, String text) { }

    /** What a step does (P-68). */
    public enum Kind { SAY, MOVE, FACE, CAMERA, MUSIC, SOUND, FADE, FLASH, WAIT, LIGHT, VANISH }

    /**
     * One step; only the fields its kind uses are set.
     *
     * @param actor   MOVE, FACE, VANISH, CAMERA (pan to): an actor id or {@code "player"}
     * @param speaker SAY: an NPC id or actor id whose portrait is shown, or null for narration
     * @param camera  CAMERA: {@code pan}, {@code zoom}, {@code shake} or {@code follow}
     * @param ms      the step's duration where it has one
     * @param state   LIGHT: a light state of the area's region; FADE: {@code out} or {@code in}
     */
    public record Step(Kind kind, @Nullable String actor, @Nullable String speaker, @Nullable String text,
                       List<Variant> variants, List<Tile> path, @Nullable String facing, @Nullable String camera,
                       @Nullable Integer x, @Nullable Integer y, @Nullable Double zoom, @Nullable Integer ms,
                       @Nullable String track, @Nullable String effect, @Nullable String state) {

        public Step {
            variants = List.copyOf(variants);
            path = List.copyOf(path);
        }
    }
}
