package com.shardbound.persistence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The last {@value #SIZE} SQL statements run by any repository, shown in the
 * Inspector panel. Written by every request thread and the autosave thread,
 * so both methods are {@code synchronized} (D26): "add, then trim to 5" must
 * happen as one step or the bound could be broken.
 */
public final class SqlLog {

    public static final int SIZE = 5;

    private static final ArrayDeque<String> RECENT = new ArrayDeque<>();

    private SqlLog() {
    }

    /** Records one statement (whitespace collapsed). Time O(1), space O(1). */
    public static synchronized void record(String sql) {
        RECENT.addLast(sql.replaceAll("\\s+", " ").trim());
        while (RECENT.size() > SIZE) {
            RECENT.removeFirst();
        }
    }

    /** Oldest-first copy of the recent statements. Time O(SIZE), space O(SIZE). */
    public static synchronized List<String> lastFive() {
        return new ArrayList<>(RECENT);
    }
}
