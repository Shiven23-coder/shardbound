package com.shardbound.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The run's last few state-changing actions by idempotency key (P-31, P-56):
 * the key, a hash of the request and the run version it produced. Kept in the
 * run row, so "applied" and "remembered" are one write.
 */
public final class RecentActions {

    /** How many actions are remembered. */
    public static final int CAPACITY = 8;

    /** One remembered action. */
    public record Entry(String key, String requestHash, long runVersion) { }

    /** How a request's key relates to the remembered actions. */
    public enum Match {
        /** Not seen: apply the action. */
        NEW,
        /** Seen with the same request: apply nothing, answer with the current view. */
        REPEAT,
        /** Seen with a different request: refuse (422). */
        REUSED
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    public Match match(ActionKey key) {
        Optional<Entry> seen = entries.stream().filter(e -> e.key().equals(key.key())).findFirst();
        if (seen.isEmpty()) {
            return Match.NEW;
        }
        return seen.get().requestHash().equals(key.requestHash()) ? Match.REPEAT : Match.REUSED;
    }

    /** Remembers an applied action, forgetting the oldest beyond {@link #CAPACITY}. O(1). */
    public void remember(ActionKey key, long runVersion) {
        entries.addLast(new Entry(key.key(), key.requestHash(), runVersion));
        while (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public void restore(List<Entry> stored) {
        entries.clear();
        entries.addAll(stored);
        while (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
    }
}
