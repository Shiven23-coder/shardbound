package com.shardbound.api;

import com.shardbound.api.dto.Requests.CombatRequest;
import com.shardbound.combat.Action;
import com.shardbound.combat.ActionType;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.SessionStore;
import io.javalin.Javalin;

/** One combat round per POST. Calls only GameEngine, which routes win/lose/flee (D21). */
public class CombatController {

    private final GameEngine engine;
    private final SessionStore store;

    public CombatController(GameEngine engine, SessionStore store) {
        this.engine = engine;
        this.store = store;
    }

    public void register(Javalin app) {
        app.post("/api/combat/action", ctx -> {
            CombatRequest body = ctx.bodyAsClass(CombatRequest.class);
            ActionType type = ActionType.valueOf(body.type());   // bad value → IllegalArgumentException → 400
            Action action = new Action(type, body.target(), body.itemId());
            ctx.json(engine.combatAction(Sessions.require(ctx, store), action));
        });
    }
}
