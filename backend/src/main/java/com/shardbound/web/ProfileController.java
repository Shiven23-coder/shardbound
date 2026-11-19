package com.shardbound.web;

import com.shardbound.persistence.PlayerRepository;
import com.shardbound.view.MetaViews.ProfileResponse;
import com.shardbound.web.Requests.ProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Player profiles: a username only, until accounts replace them in V2a. */
@RestController
@RequestMapping("/api/profiles")
@Tag(name = "profiles")
public class ProfileController {

    private final PlayerRepository players;

    public ProfileController(PlayerRepository players) {
        this.players = players;
    }

    @PostMapping
    @Operation(operationId = "createProfile", summary = "Create the profile, or return the existing one")
    public ProfileResponse create(@Valid @RequestBody ProfileRequest body) {
        String username = body.username().trim();
        return new ProfileResponse(players.findOrCreate(username), username);
    }
}
