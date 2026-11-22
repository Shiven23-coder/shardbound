package com.shardbound.engine;

import com.shardbound.event.EventBus;
import com.shardbound.item.Inventory;
import com.shardbound.model.Stats;
import com.shardbound.world.Facing;
import com.shardbound.world.WorldPosition;

/**
 * Test helper: builds a bare GameState with an EventBus that has no
 * listeners except a toast collector. Lives in the engine package so it can
 * use the package-private setBus (D30).
 */
public final class TestStates {

    private TestStates() {
    }

    public static GameState fresh() {
        return fresh(42L, false);
    }

    public static GameState fresh(long seed, boolean demoMode) {
        GameState state = new GameState("test-session", 1, "p01",
                new WorldPosition("eldmere_cottage_lane", 120, 168, Facing.DOWN), Stats.starting(), new Inventory(), seed,
                demoMode, 0);
        EventBus bus = new EventBus(state);
        bus.subscribe((event, s) -> {
            Toast toast = Toast.from(event);
            if (toast != null) {
                s.addToast(toast);
            }
        });
        state.setBus(bus);
        return state;
    }

    /** Number of toasts waiting (drains nothing). */
    public static int pendingToasts(GameState state) {
        return state.toastQueue().size();
    }
}
