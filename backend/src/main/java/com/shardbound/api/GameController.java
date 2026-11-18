package com.shardbound.api;

import com.shardbound.api.dto.Requests.ChooseRequest;
import com.shardbound.api.dto.Requests.ItemRequest;
import com.shardbound.api.dto.Requests.NewGameRequest;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.engine.SessionStore;
import com.shardbound.persistence.PlayerRepository;
import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;

/** New game, current view, choices and out-of-combat inventory. Thin: GameEngine does the work. */
public class GameController {

    private final GameEngine engine;
    private final SessionStore store;
    private final PlayerRepository players;

    public GameController(GameEngine engine, SessionStore store, PlayerRepository players) {
        this.engine = engine;
        this.store = store;
        this.players = players;
    }

    public void register(Javalin app) {
        app.post("/api/game/new", ctx -> {
            NewGameRequest body = ctx.bodyAsClass(NewGameRequest.class);
            if (!players.exists(body.playerId())) {
                throw new NotFoundResponse("No player with id " + body.playerId());
            }
            GameState state = engine.newGame(body.playerId(), body.demoMode());
            store.put(state);
            ctx.json(engine.view(state));
        });
        app.get("/api/game", ctx -> ctx.json(engine.view(Sessions.require(ctx, store))));
        app.post("/api/game/choose", ctx -> {
            ChooseRequest body = ctx.bodyAsClass(ChooseRequest.class);
            ctx.json(engine.choose(Sessions.require(ctx, store), body.choiceId()));
        });
        app.post("/api/inventory/equip", ctx -> {
            ItemRequest body = ctx.bodyAsClass(ItemRequest.class);
            ctx.json(engine.equip(Sessions.require(ctx, store), body.itemId()));
        });
        app.post("/api/inventory/use", ctx -> {
            ItemRequest body = ctx.bodyAsClass(ItemRequest.class);
            ctx.json(engine.use(Sessions.require(ctx, store), body.itemId()));
        });
    }
}
