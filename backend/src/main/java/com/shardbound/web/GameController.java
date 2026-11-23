package com.shardbound.web;

import com.shardbound.battle.BattleAction;
import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.exception.PlayerNotFoundException;
import com.shardbound.persistence.PlayerRepository;
import com.shardbound.view.SessionView;
import com.shardbound.web.Requests.ChooseRequest;
import com.shardbound.web.Requests.BattleRequest;
import com.shardbound.web.Requests.EncounterRequest;
import com.shardbound.web.Requests.ExitRequest;
import com.shardbound.web.Requests.InteractRequest;
import com.shardbound.web.Requests.RecoverRequest;
import com.shardbound.web.Requests.ItemRequest;
import com.shardbound.web.Requests.NewGameRequest;
import io.swagger.v3.oas.annotations.Operation;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RequestHeader;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Starting a game and every in-game action. Thin: the engine does the work. */
@RestController
@RequestMapping("/api")
@Tag(name = "game")
public class GameController {

    private final GameEngine engine;
    private final GameSessions sessions;
    private final PlayerRepository players;

    public GameController(GameEngine engine, GameSessions sessions, PlayerRepository players) {
        this.engine = engine;
        this.sessions = sessions;
        this.players = players;
    }

    @PostMapping("/game/new")
    @Operation(operationId = "newGame", summary = "Start a new game; the view's sessionId is the X-Session token")
    public SessionView newGame(@Valid @RequestBody NewGameRequest body) {
        if (!players.exists(body.playerId())) {
            throw new PlayerNotFoundException(body.playerId());
        }
        GameState state = engine.newGame(body.playerId(), body.demoMode());
        sessions.put(state);
        return engine.view(state);
    }

    @GetMapping("/game")
    @Operation(operationId = "getGame", summary = "The current screen of this session's game")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView game(@CurrentGame GameState state) {
        return engine.view(state);
    }

    @PostMapping("/dialogue/choose")
    @Operation(operationId = "choose", summary = "Take a dialogue choice")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView choose(@CurrentGame GameState state, @Valid @RequestBody ChooseRequest body,
                              @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("dialogue/choose", key, body),
                () -> engine.choose(state, body.choiceId()));
    }

    @PostMapping("/world/exit")
    @Operation(operationId = "worldExit", summary = "Walk through an exit into the next area (§4.11)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView exit(@CurrentGame GameState state, @Valid @RequestBody ExitRequest body,
                            @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("world/exit", key, body),
                () -> engine.exit(state, body.exitId(), body.x(), body.y(), body.facing()));
    }

    /** A rest at a Lantern writes slot 0 with the run, so the request is one transaction (P-61). */
    @PostMapping("/world/interact")
    @Transactional
    @Operation(operationId = "worldInteract",
            summary = "Talk to an NPC, use an object, or step into a trigger (§4.11)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView interact(@CurrentGame GameState state, @Valid @RequestBody InteractRequest body,
                                @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("world/interact", key, body),
                () -> engine.interact(state, body.targetId(), body.x(), body.y(), body.facing()));
    }

    @PostMapping("/world/recover")
    @Operation(operationId = "worldRecover", summary = "Stepped within a tile of the Lost Shards: they come back (§4.4)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView recover(@CurrentGame GameState state, @Valid @RequestBody RecoverRequest body,
                               @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("world/recover", key, body),
                () -> engine.recover(state, body.x(), body.y(), body.facing()));
    }

    @PostMapping("/world/encounter")
    @Operation(operationId = "worldEncounter", summary = "A creep's aggro radius was entered: the battle starts (§4.2)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView encounter(@CurrentGame GameState state, @Valid @RequestBody EncounterRequest body,
                                 @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("world/encounter", key, body),
                () -> engine.encounter(state, body.creepId(), body.x(), body.y(), body.facing()));
    }

    @PostMapping("/battle/action")
    @Operation(operationId = "battleAction", summary = "Play one battle round (§4.7)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView battleAction(@CurrentGame GameState state, @Valid @RequestBody BattleRequest body,
                                    @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("battle/action", key, body),
                () -> engine.battleAction(state, new BattleAction(body.kind(), body.slot(), body.itemId())));
    }

    @PostMapping("/inventory/equip")
    @Operation(operationId = "equip", summary = "Equip a weapon outside battle")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView equip(@CurrentGame GameState state, @Valid @RequestBody ItemRequest body,
                             @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("inventory/equip", key, body),
                () -> engine.equip(state, body.itemId()));
    }

    @PostMapping("/inventory/use")
    @Operation(operationId = "useItem", summary = "Use a consumable outside battle")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView use(@CurrentGame GameState state, @Valid @RequestBody ItemRequest body,
                           @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("inventory/use", key, body),
                () -> engine.use(state, body.itemId()));
    }
}
