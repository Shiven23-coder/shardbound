package com.shardbound.battle;

import org.jspecify.annotations.Nullable;

/**
 * One hit of the last round, for the client's floating numbers and flashes.
 *
 * @param onPlayer true if the player was hit
 * @param affinity how the target took the element, or null for status damage
 * @param status   a status the hit put on (or, for status damage, the status that dealt it), or null
 * @param element  the move's element (the client picks its effect by it), or null for status damage
 * @param line     the index of the round's text line it plays with (the move's announcement, or the
 *                 status damage line), so the arena keeps pace with the text
 */
public record HitLine(boolean onPlayer, int amount, boolean crit, @Nullable Affinity affinity,
                      @Nullable Status status, @Nullable Element element, int line) { }
