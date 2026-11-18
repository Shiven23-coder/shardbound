package com.shardbound.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the ending for a RESOLVER node (spec §6.9): walk the resolver endings
 * in priority order and return the first whose ConditionGroup passes. The
 * last one has no conditions, so a well-formed content set always resolves.
 * Endings with via_resolver = false (END_QUICK) are never considered (D11).
 */
public class EndingResolver {

    private final List<Ending> candidates;

    public EndingResolver(List<Ending> endings) {
        List<Ending> sorted = new ArrayList<>();
        for (Ending ending : endings) {
            if (ending.viaResolver()) {
                sorted.add(ending);
            }
        }
        sorted.sort(Comparator.comparingInt(Ending::priority));
        this.candidates = List.copyOf(sorted);
    }

    /** First matching ending. Time O(e · c) for e endings with c conditions each, space O(1). */
    public Ending resolve(GameState state) {
        for (Ending ending : candidates) {
            if (ending.conditions().test(state)) {
                return ending;
            }
        }
        throw new IllegalStateException("No ending matched: the last resolver ending must have no conditions");
    }

    public List<Ending> candidates() {
        return candidates;
    }
}
