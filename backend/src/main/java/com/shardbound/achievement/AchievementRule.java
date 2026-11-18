package com.shardbound.achievement;

import com.shardbound.engine.GameState;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;

import java.util.function.BiPredicate;

/**
 * Strategy for one achievement: which event type can trigger it and the test
 * to run when that event happens. Stateless, so one instance is shared by
 * every session (D23).
 */
public interface AchievementRule {

    String achievementId();

    /** The only event type this rule needs to look at. */
    EventType trigger();

    boolean matches(GameEvent event, GameState state);

    /** Builds a rule from a lambda. */
    static AchievementRule of(String achievementId, EventType trigger, BiPredicate<GameEvent, GameState> test) {
        return new AchievementRule() {
            @Override public String achievementId() { return achievementId; }
            @Override public EventType trigger()    { return trigger; }
            @Override public boolean matches(GameEvent event, GameState state) {
                return test.test(event, state);
            }
        };
    }
}
