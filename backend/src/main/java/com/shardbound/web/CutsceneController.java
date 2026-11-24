package com.shardbound.web;

import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.view.SessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cutscenes (P-68): the client ends the one playing, played out or skipped; the server applies what follows. */
@RestController
@RequestMapping("/api/cutscene")
@Tag(name = "cutscene")
public class CutsceneController {

    private final GameEngine engine;

    public CutsceneController(GameEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/finish")
    @Operation(operationId = "finishCutscene", summary = "End the cutscene playing, played out or skipped (keyed)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView finish(@CurrentGame GameState state, @Valid @RequestBody Requests.CutsceneRequest body,
                              @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("cutscene/finish", key, body),
                () -> engine.finishCutscene(state, body.cutsceneId()));
    }
}
