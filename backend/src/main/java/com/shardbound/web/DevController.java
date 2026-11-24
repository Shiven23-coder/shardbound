package com.shardbound.web;

import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.view.MetaViews.InspectorView;
import com.shardbound.view.WeaponView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Debugging aids. The bean only exists in the {@code dev} and {@code test}
 * profiles, so in production these routes are 404.
 */
@RestController
@RequestMapping("/api/debug")
@Profile({"dev", "test"})
@Tag(name = "dev")
public class DevController {

    private final GameEngine engine;

    public DevController(GameEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/weapons")
    @Operation(operationId = "weaponLadder", summary = "Every weapon rated for a new player, best first (dev only, P-65)")
    public List<WeaponView> weapons() {
        return engine.weaponLadder();
    }

    @GetMapping("/inspector")
    @Operation(operationId = "inspect", summary = "Raw state behind the current screen (dev only)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public InspectorView inspector(@CurrentGame GameState state) {
        return engine.inspect(state);
    }
}
