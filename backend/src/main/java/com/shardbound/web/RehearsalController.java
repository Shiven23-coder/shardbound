package com.shardbound.web;

import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.engine.Rehearsal;
import com.shardbound.model.Faction;
import com.shardbound.progression.Attribute;
import com.shardbound.quest.QuestStatus;
import com.shardbound.view.SessionView;
import io.swagger.v3.oas.annotations.Hidden;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Story points the end-to-end tests start from (P-67). The bean exists only in the {@code e2e} profile, which
 * the Playwright backend adds; in production (and in dev) these routes are 404, and the production bundle never
 * names them ({@code tools/ci/check-bundle.mjs}).
 */
@RestController
@RequestMapping("/api/debug")
@Profile("e2e")
@Hidden
public class RehearsalController {

    /**
     * The elder's path to the Herald's door, as {@code HeraldTest} plays it: the Ash Falls heard, the Ruins Lantern
     * kindled and rested at, the Heartshard kept; level 4 on the Vigor and Strength route with the ruins chest's
     * hatchet and Brann's Ember (the balance bot's state at the boss, §10.2). The player stands by the Lantern.
     */
    static final Rehearsal READY_FOR_THE_HERALD = new Rehearsal(
            Map.of("storm_night", 0, "dawn", 1, "herald_risen", 1, "lantern_lit_ruins", 1),
            Map.of("q_strange_light", QuestStatus.ACTIVE),
            Map.of(Faction.ELDMERE, 10),
            Map.of("maren", 10),
            List.of("heartshard"),
            "woodcutter_hatchet",
            List.of("ember"),
            List.of(Attribute.VIGOR, Attribute.STRENGTH, Attribute.STRENGTH),
            "ruins", "eldmere_old_ruins", "ruins_rest");

    private final GameEngine engine;

    public RehearsalController(GameEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/ready-for-the-herald")
    public SessionView readyForTheHerald(@CurrentGame GameState state,
                                         @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("debug/ready-for-the-herald", key, null),
                () -> engine.rehearse(state, READY_FOR_THE_HERALD));
    }
}
