package com.shardbound.web;

import com.shardbound.content.GameContent;
import com.shardbound.support.IntegrationTest;
import com.shardbound.support.MutableClock;
import com.shardbound.support.TestApi;
import com.shardbound.support.TestApi.Response;
import com.shardbound.world.AreaMap;
import com.shardbound.world.CollisionGrid;
import com.shardbound.world.CreepSpawn;
import com.shardbound.world.MapObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.BitSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encounters and battle actions over HTTP (V2_SPEC §4.2, P-54, P-56): the
 * server validates a creep encounter, a battle is fought round by round, and
 * every action is applied once however often it is retried with its key.
 */
class BattleApiIT extends IntegrationTest {

    private static final String LANE = "eldmere_cottage_lane";
    private static final String SLIME = "meadow_creep_b";
    private static final String ACTION = "/api/battle/action";
    private static final Map<String, Object> STRIKE = Map.of("kind", "STRIKE", "slot", 0);
    private static final Map<String, Object> GUARD = Map.of("kind", "GUARD", "slot", 0);

    @Autowired
    private GameContent content;

    @Autowired
    private MutableClock clock;

    private String session;
    private JsonNode view;

    /** A new game played past the opening: the player stands in Cottage Lane. */
    @BeforeEach
    void inTheLane() {
        int player = api.profile("battle-" + System.nanoTime() % 1_000_000_000);
        Response created = api.post("/api/game/new", null, Map.of("playerId", player, "demoMode", false));
        TestApi.expect(200, created);
        session = created.body().get("sessionId").asString();
        view = created.body();
        while (!"WORLD".equals(view.get("mode").asString())) {
            view = choose("Ignore");
        }
        assertEquals(LANE, view.get("world").get("areaId").asString());
    }

    private JsonNode choose(String label) {
        for (JsonNode c : view.get("dialogue").get("choices")) {
            if (c.get("label").asString().startsWith(label) || view.get("dialogue").get("choices").size() == 1) {
                Response next = api.post("/api/dialogue/choose", session, Map.of("choiceId", c.get("id").asInt()));
                TestApi.expect(200, next);
                return next.body();
            }
        }
        throw new AssertionError("No choice '" + label + "' in " + view.get("dialogue"));
    }

    /** A reachable tile centre exactly {@code distance} tiles from the creep. */
    private int[] spotAt(String creep, int distance) {
        AreaMap lane = content.world().mapOf(LANE);
        MapObject spawn = lane.object(creep).orElseThrow();
        CollisionGrid grid = lane.collisionWith(o -> true);
        JsonNode at = view.get("world").get("player");
        BitSet reachable = grid.reachableFrom(lane.tileOf(at.get("x").asInt()), lane.tileOf(at.get("y").asInt()));
        for (int y = 0; y < lane.height(); y++) {
            for (int x = 0; x < lane.width(); x++) {
                if (spawn.distanceTo(x, y) == distance && reachable.get(y * lane.width() + x)) {
                    return new int[] {lane.centre(x), lane.centre(y)};
                }
            }
        }
        throw new AssertionError("No spot " + distance + " tiles from " + creep);
    }

    private Response encounter(String creep, int distance) {
        int[] spot = spotAt(creep, distance);
        clock.advance(Duration.ofMinutes(1));                         // the walk there
        return api.post("/api/world/encounter", session,
                Map.of("creepId", creep, "x", spot[0], "y", spot[1], "facing", "DOWN"));
    }

    private JsonNode startBattle() {
        Response started = encounter(SLIME, 1);
        TestApi.expect(200, started);
        assertEquals("BATTLE", started.body().get("mode").asString());
        return started.body();
    }

    private static JsonNode withoutToasts(JsonNode view) {
        ObjectNode copy = ((ObjectNode) view).deepCopy();
        copy.remove("toasts");
        return copy;
    }

    @Test
    void anEncounterOutsideTheAggroRadiusIsRejected() {
        int radius = CreepSpawn.of(content.world().mapOf(LANE).object(SLIME).orElseThrow()).aggroRadius();
        Response tooFar = encounter(SLIME, radius + 1);
        assertEquals(409, tooFar.status());
        assertEquals("WORLD_EVENT_REJECTED", tooFar.code());
    }

    @Test
    void aBattleIsFoughtToVictoryAndTheCreepStaysDefeated() {
        JsonNode current = startBattle();
        assertEquals("Mire Slime", current.get("battle").get("enemy").get("name").asString());
        int shards = current.get("hud").get("shards").asInt();
        for (int round = 0; round < 30 && "BATTLE".equals(current.get("mode").asString()); round++) {
            Response next = api.post(ACTION, session, STRIKE);
            TestApi.expect(200, next);
            current = next.body();
        }
        assertEquals("WON", current.get("battle").get("outcome").asString(), current.get("battle").toString());
        assertEquals("WORLD", current.get("mode").asString());
        assertTrue(current.get("hud").get("shards").asInt() > shards, "Shards for the victory (P-55)");
        for (JsonNode creep : current.get("world").get("creeps")) {
            assertFalse(SLIME.equals(creep.get("id").asString()), "a defeated creep is not drawn");
        }
        view = current;
        Response again = encounter(SLIME, 1);
        assertEquals(409, again.status(), "once per respawn");
    }

    @Test
    void aRetriedActionIsAppliedOnceAndAnswersWithTheCurrentView() {
        startBattle();
        String key = TestApi.newKey();
        Response first = api.postKeyed(ACTION, session, GUARD, key);
        TestApi.expect(200, first);
        Response retry = api.postKeyed(ACTION, session, GUARD, key);    // the answer was lost: same key, same body
        TestApi.expect(200, retry);
        assertEquals(withoutToasts(first.body()), withoutToasts(retry.body()));
        assertEquals(first.body().get("battle").get("round").asInt(),
                api.get("/api/game", session).body().get("battle").get("round").asInt(), "one round, not two");
    }

    @Test
    void aKeyReusedForAnotherActionIs422() {
        startBattle();
        String key = TestApi.newKey();
        TestApi.expect(200, api.postKeyed(ACTION, session, GUARD, key));
        Response reused = api.postKeyed(ACTION, session, STRIKE, key);
        assertEquals(422, reused.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.code());
    }

    @Test
    void anActionWithoutAKeyIs400() {
        startBattle();
        Response missing = api.postKeyed(ACTION, session, STRIKE, null);
        assertEquals(400, missing.status());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", missing.code());
    }

    @Test
    void aRetriedSaveWritesTheSlotOnce() {
        String key = TestApi.newKey();
        TestApi.expect(204, api.postKeyed("/api/saves/2", session, null, key));
        JsonNode savedAt = api.get("/api/saves", session).body().get(2);
        clock.advance(Duration.ofMinutes(5));
        TestApi.expect(204, api.postKeyed("/api/saves/2", session, null, key));
        assertEquals(savedAt, api.get("/api/saves", session).body().get(2), "the retry did not save again");
    }
}
