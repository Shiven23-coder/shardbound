package com.shardbound.battle;

import com.shardbound.engine.GameState;
import com.shardbound.engine.TestBattles;
import com.shardbound.engine.TestEngines;
import com.shardbound.engine.TestStates;
import com.shardbound.item.Item;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Battle rounds (V2_SPEC §4.5–4.7, P-54) on the real content, with seeded states. */
class BattleEngineTest {

    private final BattleContent content = TestEngines.content().battle();
    private final Map<String, Item> items = TestEngines.content().items();
    private final BattleEngine engine = new BattleEngine(content, items);

    private GameState player(long seed) {
        GameState s = TestStates.fresh(seed, false);
        s.inventory().add(items.get("rusty_shortsword"), 1);
        s.inventory().equip("rusty_shortsword");
        s.learnSkill("rending_cut");
        s.learnSkill("crystal_pulse");
        return s;
    }

    private BattleState fight(GameState s, String creep, int level) {
        BattleState battle = engine.start(s, content.creeps().get(creep), level, "area/" + creep, null, true);
        TestBattles.begin(s, battle);
        return battle;
    }

    @Test
    void theFasterFighterActsFirstAndThePlayerWinsTies() {
        GameState s = player(1);
        fight(s, "storm_rat", 2);                                  // speed 8 > the player's 6
        engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
        List<String> text = s.battle().text();
        assertTrue(text.getFirst().startsWith("The Storm Rat"), text.toString());

        GameState t = player(1);
        BattleState slow = fight(t, "mire_slime", 1);               // speed 4 < 6
        engine.act(t, BattleAction.of(BattleAction.Kind.STRIKE));
        assertEquals("You strike!", slow.text().getFirst());
    }

    /** P-69: Guard goes first, so a slower player's guard blunts the faster rat's hit in the same round. */
    @Test
    void aSlowerPlayersGuardGoesFirstAndHalvesTheFasterEnemysHitThatRound() {
        GameState open = player(1);
        BattleState unguarded = fight(open, "storm_rat", 2);
        engine.act(open, BattleAction.of(BattleAction.Kind.STRIKE));
        int full = unguarded.hits().stream().filter(HitLine::onPlayer).findFirst().orElseThrow().amount();

        GameState s = player(1);                                     // the same seed: the same Bite, the same roll
        BattleState battle = fight(s, "storm_rat", 2);
        engine.act(s, BattleAction.of(BattleAction.Kind.GUARD));
        assertEquals("You raise your guard.", battle.text().getFirst(), "the guard goes before the faster rat");
        int halved = battle.hits().stream().filter(HitLine::onPlayer).findFirst().orElseThrow().amount();
        assertTrue(Math.abs(halved * 2 - full) <= 1, "guarded " + halved + " of " + full);
    }

    /**
     * Rewritten for P-69. It used to pin the old order, where the faster rat landed its announced Frenzy before a
     * guard chosen that round went up, so only a guard raised the round before could blunt it. Now the guard goes
     * first and lasts to the end of the round: the Frenzy announced last round lands on it at a quarter.
     */
    @Test
    void aTelegraphedMoveIsAnnouncedFirstAndAGuardChosenTheNextRoundCutsItToAQuarter() {
        GameState s = player(7);
        BattleState battle = fight(s, "storm_rat", 2);
        Skill frenzy = content.skills().get("rat_frenzy");
        battle.enemy().announce(frenzy);
        assertTrue(battle.telegraph().contains("Storm Rat"), battle.telegraph());
        s.stats().heal(100);
        engine.act(s, BattleAction.of(BattleAction.Kind.GUARD));
        assertEquals("You raise your guard.", battle.text().getFirst());
        int guarded = 100 - s.stats().hp();
        double raw = 13 * 2.0 * 100 / 110.0;                        // atk 13 at level 2, DEF 10
        assertTrue(guarded > 0 && guarded <= Math.ceil(raw * 0.25 * 1.1 * 1.5), "guarded frenzy: " + guarded);
        assertFalse(battle.enemy().statuses().has(Status.EXPOSED), "only a boss is exposed by a guarded telegraph");
    }

    /** P-69: an enemy's Guard goes first too: the slower slime braces before the player's strike lands. */
    @Test
    void anEnemysGuardAlsoGoesFirst() {
        GameState s = player(3);
        BattleState battle = fight(s, "mire_slime", 1);              // speed 4 < the player's 6
        battle.enemy().takeDamage(battle.enemy().maxHp() - 5);      // below 30 %: it will guard
        engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
        assertEquals("The Mire Slime braces itself.", battle.text().getFirst(), battle.text().toString());
        assertTrue(battle.enemy().guarding());
    }

    @Test
    void aDefensiveCreepGuardsOnceWhenLow() {
        GameState s = player(3);
        BattleState battle = fight(s, "mire_slime", 1);
        battle.enemy().takeDamage(battle.enemy().maxHp() - 5);      // below 30 %
        engine.act(s, BattleAction.of(BattleAction.Kind.GUARD));
        assertTrue(battle.text().contains("The Mire Slime braces itself."), battle.text().toString());
        assertTrue(battle.enemy().guardedOnce());
    }

    @Test
    void aSkillSpendsItsFpAndVictoryRefillsItWithShards() {
        GameState s = player(11);
        BattleState battle = fight(s, "mire_slime", 1);
        battle.enemy().takeDamage(battle.enemy().maxHp() - 1);
        int fpBefore = s.stats().fp();
        BattleOutcome outcome = engine.act(s, BattleAction.skill(0));            // Rending Cut, 5 FP
        assertEquals(BattleOutcome.WON, outcome);
        assertEquals(fpBefore, s.stats().fp(), "FP refills after a victory (P-55)");
        assertEquals(35, s.stats().shards(), "a level-1 creep: round(35 · 1^1.45)");
        assertEquals(35, battle.rewards().shards());
    }

    @Test
    void fleeChanceUsesCurrentSpeedsClampedTenToNinety() {
        GameState s = player(1);
        assertEquals(50 + (6 - 8) * 5, engine.fleeChance(fight(s, "storm_rat", 2)));
        BattleState slime = fight(player(1), "mire_slime", 1);
        assertEquals(60, engine.fleeChance(slime));
        slime.player().statuses().apply(content.status(Status.FROST));         // −3 speed
        assertEquals(45, engine.fleeChance(slime));
    }

    @Test
    void aSeedReproducesABattleExactly() {
        List<String> first = playOut(42);
        List<String> second = playOut(42);
        assertEquals(first, second);
        assertFalse(first.isEmpty());
    }

    private List<String> playOut(long seed) {
        GameState s = player(seed);
        BattleState battle = fight(s, "storm_rat", 3);
        List<String> lines = new ArrayList<>();
        for (int round = 0; round < 20 && battle.outcome() == BattleOutcome.ONGOING; round++) {
            engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
            lines.addAll(battle.text());
        }
        return lines;
    }

    @Test
    void aStatusTickCanEndTheBattle() {
        GameState s = player(5);
        BattleState battle = fight(s, "mire_slime", 1);
        battle.enemy().takeDamage(battle.enemy().maxHp() - 1);
        battle.enemy().statuses().apply(content.status(Status.POISON));
        BattleOutcome outcome = engine.act(s, BattleAction.of(BattleAction.Kind.GUARD));
        assertEquals(BattleOutcome.WON, outcome);
        assertTrue(battle.text().stream().anyMatch(l -> l.contains("poison")), battle.text().toString());
    }

    @Test
    void eachHitPointsAtTheLineThatAnnouncesItSoTheArenaKeepsPaceWithTheText() {
        GameState s = player(3);
        BattleState battle = fight(s, "storm_rat", 2);
        battle.enemy().statuses().apply(content.status(Status.POISON));
        engine.act(s, BattleAction.of(BattleAction.Kind.STRIKE));
        List<String> text = battle.text();
        assertFalse(battle.hits().isEmpty());
        for (HitLine hit : battle.hits()) {
            String line = text.get(hit.line());
            if (hit.element() == null) {
                assertTrue(line.contains(" from "), line);                // status damage: its own line
            } else {
                assertTrue(line.endsWith("!") && (line.contains("strike") || line.contains("attacks")
                        || line.contains("use")), line);                // a move: its announcement
            }
        }
    }
}
