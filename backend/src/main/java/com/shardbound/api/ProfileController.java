package com.shardbound.api;

import com.shardbound.api.dto.MetaViews.ProfileResponse;
import com.shardbound.api.dto.Requests.ProfileRequest;
import com.shardbound.persistence.PlayerRepository;
import io.javalin.Javalin;

/** POST /api/profiles: create the profile or return the existing one (S4). */
public class ProfileController {

    public static final int MAX_USERNAME = 30;   // player.username VARCHAR(30)

    private final PlayerRepository players;

    public ProfileController(PlayerRepository players) {
        this.players = players;
    }

    public void register(Javalin app) {
        app.post("/api/profiles", ctx -> {
            ProfileRequest body = ctx.bodyAsClass(ProfileRequest.class);
            String username = body.username() == null ? "" : body.username().trim();
            if (username.isEmpty() || username.length() > MAX_USERNAME) {
                throw new IllegalArgumentException("Username must be 1–" + MAX_USERNAME + " characters");
            }
            ctx.json(new ProfileResponse(players.findOrCreate(username), username));
        });
    }
}
