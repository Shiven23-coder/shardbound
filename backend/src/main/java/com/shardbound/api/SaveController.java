package com.shardbound.api;

import com.shardbound.api.dto.MetaViews.OkResponse;
import com.shardbound.api.dto.MetaViews.SlotView;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.engine.SessionStore;
import com.shardbound.persistence.SaveData.SlotSummary;
import com.shardbound.story.StoryNode;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Save slots. Calls only GameEngine (D22), which locks, reseeds and checks the mode. */
public class SaveController {

    public static final int SLOT_COUNT = 4;   // 0 = autosave, 1..3 manual

    private final GameEngine engine;
    private final SessionStore store;

    public SaveController(GameEngine engine, SessionStore store) {
        this.engine = engine;
        this.store = store;
    }

    public void register(Javalin app) {
        app.get("/api/saves", ctx -> ctx.json(slots(Sessions.playerId(ctx, store))));
        app.post("/api/saves/{slot}", ctx -> {
            engine.save(Sessions.require(ctx, store), slot(ctx));
            ctx.json(OkResponse.OK);
        });
        app.post("/api/saves/{slot}/load", ctx -> {
            GameState current = Sessions.optional(ctx, store);
            int playerId = Sessions.playerId(ctx, store);
            String sessionId = current == null ? null : current.sessionId();
            GameState loaded = engine.load(playerId, slot(ctx), sessionId);   // new session if none was sent
            store.put(loaded);
            ctx.json(engine.view(loaded));
        });
        app.delete("/api/saves/{slot}", ctx -> {
            engine.deleteSave(Sessions.playerId(ctx, store), slot(ctx));
            ctx.json(OkResponse.OK);
        });
    }

    /** Always four entries (slots 0..3); empty slots have exists=false. O(slots). */
    private List<SlotView> slots(int playerId) {
        Map<Integer, SlotSummary> existing = new HashMap<>();
        for (SlotSummary summary : engine.listSaves(playerId)) {
            existing.put(summary.slot(), summary);
        }
        List<SlotView> result = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            SlotSummary s = existing.get(slot);
            if (s == null) {
                result.add(new SlotView(slot, false, null, null, null, null, null));
            } else {
                StoryNode node = engine.content().graph().node(s.currentNode());
                String title = node == null ? s.currentNode() : node.title();
                Integer chapter = node == null ? null : node.chapter();
                result.add(new SlotView(slot, true, title, chapter, s.level(), s.playSeconds(), s.updatedAt()));
            }
        }
        return result;
    }

    private static int slot(Context ctx) {
        int slot = Sessions.parseInt(ctx.pathParam("slot"), "slot");
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IllegalArgumentException("slot must be 0–3");
        }
        return slot;
    }
}
