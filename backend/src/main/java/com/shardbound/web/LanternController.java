package com.shardbound.web;

import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.view.LevelPreviewView;
import com.shardbound.view.SessionView;
import com.shardbound.web.Requests.LevelUpRequest;
import com.shardbound.web.Requests.LoadoutRequest;
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

/**
 * The Lantern menu (V2_SPEC §4.1, §4.3, P-61): level-up with a preview, the
 * skill loadout, and leaving. The preview changes nothing and takes no
 * idempotency key; every other action does (P-56). Thin: the engine does the work.
 */
@RestController
@RequestMapping("/api/lantern")
@Tag(name = "lantern")
public class LanternController {

    private final GameEngine engine;

    public LanternController(GameEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/levelup/preview")
    @Operation(operationId = "lanternLevelUpPreview",
            summary = "What raising these attributes would cost and change; nothing is applied (§4.3)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public LevelPreviewView preview(@CurrentGame GameState state, @Valid @RequestBody LevelUpRequest body) {
        return engine.previewLevelUp(state, body.raises());
    }

    @PostMapping("/levelup")
    @Operation(operationId = "lanternLevelUp", summary = "Buy the levels of these raises with held Shards (§4.3)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView levelUp(@CurrentGame GameState state, @Valid @RequestBody LevelUpRequest body,
                               @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("lantern/levelup", key, body),
                () -> engine.levelUp(state, body.raises()));
    }

    @PostMapping("/loadout")
    @Operation(operationId = "lanternLoadout", summary = "Set the four skill slots (§4.1)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView loadout(@CurrentGame GameState state, @Valid @RequestBody LoadoutRequest body,
                               @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("lantern/loadout", key, body),
                () -> engine.setLoadout(state, body.slots()));
    }

    @PostMapping("/leave")
    @Operation(operationId = "lanternLeave", summary = "Close the Lantern menu and walk on")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public SessionView leave(@CurrentGame GameState state,
                             @Parameter(name = IdempotencyKeys.HEADER, in = ParameterIn.HEADER, required = true, description = "A new UUID per action (P-56)") @RequestHeader(name = IdempotencyKeys.HEADER, required = false) @Nullable String key) {
        return engine.once(state, IdempotencyKeys.of("lantern/leave", key, null), () -> engine.leaveLantern(state));
    }
}
