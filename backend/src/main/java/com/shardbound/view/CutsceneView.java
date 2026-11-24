package com.shardbound.view;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The cutscene playing (CUTSCENE mode, P-68): its actors and the steps the client stages in order. A
 * {@code say} step's text is already chosen for this player (its variants resolved on the server).
 * The client ends it, played out or skipped, with {@code POST /api/cutscene/finish}.
 */
public record CutsceneView(String id, List<ActorView> actors, List<StepView> steps) {

    /** A sprite (an art manifest key) placed at a tile for the cutscene. */
    public record ActorView(String id, String sprite, int x, int y, String facing) { }

    public record TileView(int x, int y) { }

    /**
     * One step; only the fields its kind uses are set.
     *
     * @param kind SAY, MOVE, FACE, CAMERA, MUSIC, SOUND, FADE, FLASH, WAIT, LIGHT or VANISH
     */
    public record StepView(String kind, @Nullable String actor, @Nullable String speaker, @Nullable String text,
                           List<TileView> path, @Nullable String facing, @Nullable String camera,
                           @Nullable Integer x, @Nullable Integer y, @Nullable Double zoom, @Nullable Integer ms,
                           @Nullable String track, @Nullable String effect, @Nullable String state) { }
}
