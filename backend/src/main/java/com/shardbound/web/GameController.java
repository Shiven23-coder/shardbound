package com.shardbound.web;

import com.shardbound.combat.Action;
import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.exception.PlayerNotFoundException;
import com.shardbound.persistence.PlayerRepository;
import com.shardbound.view.GameView;
import com.shardbound.web.Requests.ChooseRequest;
import com.shardbound.web.Requests.CombatRequest;
import com.shardbound.web.Requests.ItemRequest;
import com.shardbound.web.Requests.NewGameRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    public GameView newGame(@Valid @RequestBody NewGameRequest body) {
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
    public GameView game(@CurrentGame GameState state) {
        return engine.view(state);
    }

    @PostMapping("/game/choose")
    @Operation(operationId = "choose", summary = "Take a story choice")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public GameView choose(@CurrentGame GameState state, @Valid @RequestBody ChooseRequest body) {
        return engine.choose(state, body.choiceId());
    }

    @PostMapping("/combat/action")
    @Operation(operationId = "combatAction", summary = "Play one combat round")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public GameView combatAction(@CurrentGame GameState state, @Valid @RequestBody CombatRequest body) {
        return engine.combatAction(state, new Action(body.type(), body.target(), body.itemId()));
    }

    @PostMapping("/inventory/equip")
    @Operation(operationId = "equip", summary = "Equip a weapon outside combat")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public GameView equip(@CurrentGame GameState state, @Valid @RequestBody ItemRequest body) {
        return engine.equip(state, body.itemId());
    }

    @PostMapping("/inventory/use")
    @Operation(operationId = "useItem", summary = "Use a consumable outside combat")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public GameView use(@CurrentGame GameState state, @Valid @RequestBody ItemRequest body) {
        return engine.use(state, body.itemId());
    }
}
