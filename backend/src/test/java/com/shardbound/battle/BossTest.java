package com.shardbound.battle;

import com.shardbound.engine.GameState;
import com.shardbound.engine.TestBattles;
import com.shardbound.engine.TestEngines;
import com.shardbound.engine.TestStates;
import com.shardbound.item.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Ashen Herald (V2_SPEC §4.7, P-67): two phases from HP, a telegraph cadence, "Guard the fall", rewards. */
class BossTest {

    private final BattleContent content = TestEngines.content().battle();
    private final Map<String, Item> items = TestEngines.content().items();
    private final BattleEngine engine = new BattleEngine(content, items);
    private final BossTemplate herald = content.bosses().get("ashen_herald");

    private GameState player(long seed) {
        GameState s = TestStates.fresh(seed, false);
        s.inventory().add(items.get("woodcutter_hatchet"), 1);
        s.inventory().equip("woodcutter_hatchet");
        s.learnSkill("rending_cut");
        return s;
    }

    private BattleState fight(GameState s) {
        BattleState battle = engine.startBoss(s, herald);
        TestBattles.begin(s, battle);
        return battle;
    }

    private static BattleAction guard() {
        return BattleAction.of(BattleAction.Kind.GUARD);
    }

    @Test
    void theHeraldIsALevelFiveAshenBossThatCannotBeFled() {
        BattleState battle = fight(player(1));
        assertEquals("Ashen Herald", battle.enemy().name());
        assertEquals(5, battle.enemy().level());
        assertEquals(Family.ASHEN, battle.enemy().family());
        assertFalse(battle.canFlee(), "Flee is disabled in boss fights (§4.7)");
        assertEquals(1, battle.enemy().phase());
        assertEquals("The Ashen Herald stands before you!", battle.text().getLast());
    }

    @Test
    void phaseTwoBeginsAtHalfHealthWithItsLineOnceAndAStunGuard() {
        GameState s = player(2);
        BattleState battle = fight(s);
        CreepFighter enemy = battle.enemy();
        enemy.restore(enemy.maxHp() / 2 + 3, false, List.of());       // just above half
        engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
        assertEquals(2, enemy.phase());
        assertTrue(battle.text().contains(herald.phases().get(1).line()), battle.text().toString());
        assertNotNull(battle.phaseLine(), "the client plays the transformation on this line");
        assertTrue(enemy.statuses().immunities().containsKey(Status.STUN), "Stun-immune as it turns");

        engine.act(s, guard());
        assertFalse(battle.text().contains(herald.phases().get(1).line()), "the line plays once");
        assertNull(battle.phaseLine());
    }

    /**
     * The cadence (P-67): one in every n-th of the boss's moves is its phase's telegraph, and a telegraph takes two
     * of its turns (the announcement, then the blow), so it is announced every (n + 1)-th round: phase 1 (every 3rd
     * move) strikes twice between Cinderfalls, phase 2 (every 2nd) once between Pyre Descents.
     */
    @Test
    void telegraphsComeEveryThirdMoveThenEverySecondWithOrdinaryBlowsBetween() {
        GameState s = player(3);
        BattleState battle = fight(s);
        for (int i = 0; i < 8; i++) {
            s.stats().heal(1000);
            engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
            battle.enemy().restore(battle.enemy().maxHp(), false, battle.enemy().pendingTelegraphs());   // stay in phase 1
            boolean announced = battle.text().stream().anyMatch(line -> line.contains("gathers the falling ash"));
            assertEquals(battle.round() % 4 == 0, announced, "phase 1 announces Cinderfall every 4th round: " + battle.round());
        }
        battle.enemy().restore(battle.enemy().maxHp() / 3, false, battle.enemy().pendingTelegraphs());   // phase 2
        boolean previousLanded = false;
        int landings = 0;
        for (int i = 0; i < 9; i++) {
            s.stats().heal(1000);
            engine.act(s, guard());
            battle.enemy().restore(battle.enemy().maxHp() / 3, false, battle.enemy().pendingTelegraphs());
            boolean announced = battle.text().stream().anyMatch(line -> line.contains("spreads its burning wings"));
            boolean landed = battle.text().stream().anyMatch(line -> line.contains("uses Pyre Descent"));
            assertEquals(battle.round() % 3 == 0, announced, "phase 2 announces Pyre Descent every 3rd round: " + battle.round());
            if (previousLanded) {
                assertFalse(announced || landed, "an ordinary blow follows each Pyre Descent: " + battle.text());
            }
            previousLanded = landed;
            landings += landed ? 1 : 0;
        }
        assertTrue(landings >= 2, "Pyre Descent lands again and again");
    }

    @Test
    void guardingATelegraphedBlowLeavesTheHeraldExposed() {
        GameState s = player(4);
        BattleState battle = fight(s);
        double unexposed = battle.enemy().defence(content);
        battle.enemy().announce(content.skills().get("cinderfall"));
        s.stats().heal(1000);
        engine.act(s, guard());                                        // the guard goes first; Cinderfall lands on it (P-69)

        assertTrue(battle.enemy().statuses().has(Status.EXPOSED), battle.text().toString());
        assertTrue(battle.text().contains(herald.exposeLine()), battle.text().toString());
        assertEquals(unexposed * 0.5, battle.enemy().defence(content), 1e-9, "Exposed halves its defence");
    }

    @Test
    void aGuardedOrdinaryHitDoesNotExpose() {
        GameState s = player(5);
        BattleState battle = fight(s);
        s.stats().heal(1000);
        engine.act(s, guard());                                        // round 2: not a telegraph round
        assertTrue(battle.hits().stream().anyMatch(HitLine::onPlayer), battle.text().toString());
        assertFalse(battle.enemy().statuses().has(Status.EXPOSED));
    }

    @Test
    void anUnguardedTelegraphDoesNotExpose() {
        GameState s = player(5);
        BattleState battle = fight(s);
        battle.enemy().announce(content.skills().get("cinderfall"));
        s.stats().heal(1000);
        engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
        assertFalse(battle.enemy().statuses().has(Status.EXPOSED));
    }

    @Test
    void victoryPaysFiveTimesTheCreepShardsAndTheIronLongsword() {
        GameState s = player(6);
        BattleState battle = fight(s);
        battle.enemy().restore(1, false, List.of());
        int before = s.stats().shards();
        assertEquals(BattleOutcome.WON, engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE)));
        assertEquals(before + 5 * content.rules().creepShards(5), s.stats().shards(), "1,805 Shards");
        assertTrue(s.inventory().has("iron_longsword"));
        assertEquals(List.of("iron_longsword"), battle.rewards().itemIds());
    }

    @Test
    void aLongswordAlreadyCarriedBecomesItsFallbackShards() {
        GameState s = player(7);
        s.inventory().add(items.get("iron_longsword"), 1);
        BattleState battle = fight(s);
        battle.enemy().restore(1, false, List.of());
        int before = s.stats().shards();
        engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
        assertEquals(before + 5 * content.rules().creepShards(5) + content.rules().creepShards(5), s.stats().shards(),
                "never empty (P-65): one level-5 creep's Shards instead");
        assertEquals(List.of(), battle.rewards().itemIds());
        assertEquals(1, s.inventory().count("iron_longsword"));
    }
}
