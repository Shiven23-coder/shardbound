package com.shardbound.engine;

import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.content.ContentLoader;
import com.shardbound.content.GameContent;
import com.shardbound.story.Choice;
import com.shardbound.story.StoryGraph;
import com.shardbound.view.GameView;

import java.nio.file.Path;
import java.util.List;

/** Test helpers: the real content, an in-memory engine and the quick route. */
public final class TestEngines {

    /** The quick ending route (spec §7.1). */
    public static final List<String> QUICK_ROUTE = List.of(
            "p01", "p02", "p03", "p04m", "p05m", "p06m", "p07m", "p08m", "p09m", "p10m", "END_QUICK");
    public static final long QUICK_ROUTE_SEED = 42L;

    private static GameContent content;

    private TestEngines() {
    }

    /** The real content/ folder, loaded and validated once per test JVM. */
    public static synchronized GameContent content() {
        if (content == null) {
            content = ContentLoader.fromDirectory(Path.of("../content"));
        }
        return content;
    }

    /** An engine over the real content with no persistence. */
    public static GameEngine inMemory() {
        return GameEngine.inMemory(content());
    }

    /**
     * Plays {@link #QUICK_ROUTE} through the engine: at every node it takes
     * the first UNLOCKED choice leading to the next route node, and in combat
     * it attacks until the fight ends. Demo Mode, fixed seed.
     *
     * @return the final view; its ending is END_QUICK on success
     */
    public static GameView playQuickRoute(GameEngine engine, GameState state) {
        StoryGraph graph = engine.content().graph();
        GameView view = engine.view(state);
        int guard = 0;
        while (view.mode() != Mode.ENDED && guard++ < 200) {
            if (view.mode() == Mode.COMBAT) {
                view = engine.combatAction(state, Action.of(ActionType.ATTACK));
                continue;
            }
            int index = QUICK_ROUTE.indexOf(view.node().id());
            if (index < 0 || index + 1 >= QUICK_ROUTE.size()) {
                throw new AssertionError("Left the quick route at " + view.node().id());
            }
            Choice next = unlockedChoiceTo(graph, state, view.node().id(), QUICK_ROUTE.get(index + 1));
            if (next == null) {
                throw new AssertionError("No unlocked choice from " + view.node().id() + " to "
                        + QUICK_ROUTE.get(index + 1));
            }
            view = engine.choose(state, next.id());
        }
        return view;
    }

    private static Choice unlockedChoiceTo(StoryGraph graph, GameState state, String from, String to) {
        for (Choice choice : graph.choicesFrom(from)) {
            if (choice.to().equals(to) && choice.conditions().test(state)) {
                return choice;
            }
        }
        return null;
    }
}
