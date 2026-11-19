package com.shardbound.web;

import com.shardbound.config.OpenApiConfig;
import com.shardbound.engine.GameEngine;
import com.shardbound.engine.GameState;
import com.shardbound.model.Location;
import com.shardbound.save.SlotSummary;
import com.shardbound.view.GameView;
import com.shardbound.view.MetaViews.SlotView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Save slots: 0 is the autosave, 1–3 are manual. The engine checks mode and slot rules (D22). */
@RestController
@RequestMapping("/api/saves")
@Validated
@Tag(name = "saves")
public class SaveController {

    public static final int SLOT_COUNT = 4;

    private final GameEngine engine;
    private final GameSessions sessions;

    public SaveController(GameEngine engine, GameSessions sessions) {
        this.engine = engine;
        this.sessions = sessions;
    }

    @GetMapping
    @Operation(operationId = "listSaves", summary = "All four slots; empty slots have exists=false")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public List<SlotView> list(@CurrentGame(required = false) @Nullable GameState state,
                               @RequestParam(required = false) @Nullable Integer playerId) {
        Map<Integer, SlotSummary> existing = new HashMap<>();
        for (SlotSummary summary : engine.listSaves(PlayerIds.resolve(state, playerId))) {
            existing.put(summary.slot(), summary);
        }
        List<SlotView> result = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            SlotSummary s = existing.get(slot);
            if (s == null) {
                result.add(new SlotView(slot, false, null, null, null, null, null));
            } else {
                Location location = engine.content().locationOf(s.areaId());
                String areaName = location == null ? s.areaId() : location.name();
                result.add(new SlotView(slot, true, s.areaId(), areaName, s.level(), s.playSeconds(),
                        s.updatedAt().toString()));
            }
        }
        return result;
    }

    @PostMapping("/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "saveGame", summary = "Save this session's game to slot 1, 2 or 3")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public void save(@CurrentGame GameState state, @PathVariable @Min(0) @Max(SLOT_COUNT - 1) int slot) {
        engine.save(state, slot);
    }

    @PostMapping("/{slot}/load")
    @Operation(operationId = "loadGame", summary = "Load a slot into this session (or a new one)")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public GameView load(@CurrentGame(required = false) @Nullable GameState current,
                         @PathVariable @Min(0) @Max(SLOT_COUNT - 1) int slot,
                         @RequestParam(required = false) @Nullable Integer playerId) {
        String sessionId = current == null ? null : current.sessionId();
        GameState loaded = engine.load(PlayerIds.resolve(current, playerId), slot, sessionId);
        sessions.put(loaded);
        return engine.view(loaded);
    }

    @DeleteMapping("/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteSave", summary = "Delete a slot")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    public void delete(@CurrentGame(required = false) @Nullable GameState state,
                       @PathVariable @Min(0) @Max(SLOT_COUNT - 1) int slot,
                       @RequestParam(required = false) @Nullable Integer playerId) {
        engine.deleteSave(PlayerIds.resolve(state, playerId), slot);
    }
}
