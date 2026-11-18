package com.shardbound.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardbound.api.dto.ChoiceView;
import com.shardbound.api.dto.GameView;
import com.shardbound.api.dto.HudView;
import com.shardbound.api.dto.Requests;
import com.shardbound.api.dto.ToastView;
import com.shardbound.exception.ActionNotAllowedException;
import com.shardbound.exception.InvalidChoiceException;
import com.shardbound.exception.LockedChoiceException;
import com.shardbound.exception.SaveFailedException;
import com.shardbound.exception.SaveNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON field names React relies on (spec §9.2) and the HTTP status for
 * every game exception (spec §9.1, D27). Jackson serialises records by their
 * component names, so renaming a record field would break the frontend; this
 * test catches that.
 */
class JsonContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void gameViewUsesTheContractFieldNames() throws Exception {
        GameView view = new GameView("sid", "STORY", "VILLAGE", "eldmere_square", "Eldmere Square", 0, false,
                new GameView.NodeInfo("p03", "The Square at Dawn", "text"),
                List.of(new ChoiceView(14, "Ask the Wardens", true, "Requires flag met_wardens = 1")),
                new HudView(40, 40, 3, 3, 15, 1, 0, 100, 6, 2, new HudView.WeaponInfo("rusty_shortsword", "Rusty", "F", 3)),
                new GameView.InventoryView(1, 20, List.of()), List.of(), List.of(), List.of(), List.of(),
                List.of(new ToastView("ITEM", "Obtained: Heartshard")), null, null, false);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(view));
        assertEquals("sid", json.get("sessionId").asText());
        assertEquals("Requires flag met_wardens = 1", json.get("choices").get(0).get("lockReason").asText());
        assertTrue(json.get("choices").get(0).get("locked").asBoolean());
        assertEquals("F", json.get("hud").get("weapon").get("tier").asText());
        assertEquals(100, json.get("hud").get("xpToNext").asInt());
        assertEquals(20, json.get("inventory").get("capacity").asInt());
        assertTrue(json.get("combat").isNull());
        assertTrue(json.has("offerReload"));
    }

    @Test
    void requestBodiesDeserialise() throws Exception {
        Requests.CombatRequest combat = mapper.readValue(
                "{\"type\":\"ATTACK\",\"target\":1,\"itemId\":null}", Requests.CombatRequest.class);
        assertEquals("ATTACK", combat.type());
        assertEquals(1, combat.target());
        Requests.NewGameRequest game = mapper.readValue("{\"playerId\":3,\"demoMode\":true}", Requests.NewGameRequest.class);
        assertTrue(game.demoMode());
    }

    @Test
    void everyGameExceptionHasTheSpecStatus() {
        assertEquals(400, ErrorHandler.statusFor(new InvalidChoiceException(1)));
        assertEquals(403, ErrorHandler.statusFor(new LockedChoiceException("x", "y")));
        assertEquals(404, ErrorHandler.statusFor(new SaveNotFoundException(1, 2)));
        assertEquals(409, ErrorHandler.statusFor(new ActionNotAllowedException("x")));
        assertEquals(500, ErrorHandler.statusFor(new SaveFailedException("x", new RuntimeException())));
    }
}
