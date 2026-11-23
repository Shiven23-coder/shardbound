package com.shardbound.engine;

import com.shardbound.battle.BattleAction;
import com.shardbound.support.MutableClock;
import com.shardbound.support.QuickRoute;
import com.shardbound.view.ChoiceView;
import com.shardbound.view.SessionView;
import com.shardbound.world.AreaMap;
import com.shardbound.world.MapObject;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Plays route steps through an engine the way a client would: dialogue
 * choices by label, walking up to exits and objects (with the clock moved
 * on, so the speed check sees real walking time), and winning battles.
 */
public final class RoutePlayer {

    private static final String DRAUGHT = "lantern_draught";

    /** Long enough to walk anywhere in an area. */
    public static final Duration WALK = Duration.ofMinutes(1);

    private final GameEngine engine;
    private final GameState state;
    private final MutableClock clock;
    private SessionView view;

    public RoutePlayer(GameEngine engine, GameState state, MutableClock clock) {
        this.engine = engine;
        this.state = state;
        this.clock = clock;
        this.view = engine.view(state);
    }

    public SessionView view() {
        return view;
    }

    public GameState state() {
        return state;
    }

    /** Takes the unlocked choice whose label starts with {@code label}, then wins any fight it starts. */
    public SessionView choose(String label) {
        if (view.dialogue() == null) {
            throw new AssertionError("No dialogue open (mode " + view.mode() + ") to choose '" + label + "'");
        }
        for (ChoiceView c : view.dialogue().choices()) {
            if (c.label().startsWith(label) && !c.locked()) {
                view = engine.choose(state, c.id());
                return fight();
            }
        }
        throw new AssertionError("No unlocked choice '" + label + "' on " + view.dialogue().nodeId());
    }

    public SessionView exit(String exitId) {
        int[] spot = spotFor(exitId);
        view = engine.exit(state, exitId, spot[0], spot[1], state.position().facing());
        return view;
    }

    public SessionView interact(String targetId) {
        int[] spot = spotFor(targetId);
        view = engine.interact(state, targetId, spot[0], spot[1], state.position().facing());
        return fight();
    }

    public SessionView step(QuickRoute.Step step) {
        return switch (step) {
            case QuickRoute.Choose c -> choose(c.label());
            case QuickRoute.Exit e -> exit(e.exitId());
            case QuickRoute.Interact i -> interact(i.targetId());
        };
    }

    public SessionView play(List<QuickRoute.Step> steps) {
        for (QuickRoute.Step step : steps) {
            step(step);
        }
        return view;
    }

    /** Plays steps until {@code stop} holds for the current view; fails if it never does. */
    public SessionView playUntil(List<QuickRoute.Step> steps, Predicate<SessionView> stop) {
        for (QuickRoute.Step step : steps) {
            if (stop.test(view)) {
                return view;
            }
            step(step);
        }
        if (!stop.test(view)) {
            throw new AssertionError("The route ended before the stop condition held");
        }
        return view;
    }

    /** Walks to a spot from which the object can be used; the clock moves on by {@link #WALK}. */
    private int[] spotFor(String objectName) {
        AreaMap map = engine.content().world().mapOf(state.position().areaId());
        MapObject target = map.object(objectName)
                .orElseThrow(() -> new AssertionError(objectName + " is not in " + map.key()));
        clock.advance(WALK);
        return QuickRoute.standingSpot(map, WorldObjects.collision(state, state.position().areaId(), map), target,
                map.tileOf(state.position().x()), map.tileOf(state.position().y()),
                engine.content().worldRules().interactRange());
    }

    /** Wins any battle the way a careful player would: a draught below 40 % HP, otherwise Strike. */
    private SessionView fight() {
        while (view.mode() == Mode.BATTLE) {
            boolean low = state.stats().hp() * 100 < state.stats().maxHp() * 40;
            view = engine.battleAction(state, low && state.flaskCharges(DRAUGHT) > 0
                    ? BattleAction.item(DRAUGHT) : BattleAction.of(BattleAction.Kind.STRIKE));
        }
        return view;
    }
}
