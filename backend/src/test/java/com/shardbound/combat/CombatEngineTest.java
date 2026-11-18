package com.shardbound.combat;

import com.shardbound.engine.GameState;
import com.shardbound.engine.TestStates;
import com.shardbound.item.Consumable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CombatEngine rules (spec §6.6, D20, D21, S1, S2). */
class CombatEngineTest {

    private static final EnemyTemplate WEAK = new EnemyTemplate("weak", "Rat", 1, 1, 0, 1, 25, 7, "AGGRESSIVE");
    private static final EnemyTemplate BRUTE = new EnemyTemplate("brute", "Brute", 999, 200, 0, 9, 0, 0, "AGGRESSIVE");
    private static final EnemyTemplate SLOW = new EnemyTemplate("slow", "Slug", 50, 1, 0, 0, 0, 0, "AGGRESSIVE");
    private static final EnemyTemplate FAST = new EnemyTemplate("fast", "Hare", 50, 1, 0, 20, 0, 0, "AGGRESSIVE");

    private final CombatEngine engine = new CombatEngine(
            Map.of("weak", WEAK, "brute", BRUTE, "slow", SLOW, "fast", FAST),
            Map.of("rats", new Encounter("rats", "Two Rats", true, List.of(new EncounterSlot("weak", 2))),
                   "brute", new Encounter("brute", "The Brute", false, List.of(new EncounterSlot("brute", 1))),
                   "slow", new Encounter("slow", "Slug", true, List.of(new EncounterSlot("slow", 1))),
                   "fast", new Encounter("fast", "Hare", true, List.of(new EncounterSlot("fast", 1)))));

    private static GameState stateIn(CombatEngine engine, String encounter) {
        GameState s = TestStates.fresh();
        s.stats().spendEnergy(3);
        CombatState combat = engine.start(s, encounter);
        setCombat(s, combat);
        return s;
    }

    /** GameState.setCombat is package-private to engine; tests reach it through TestCombat. */
    private static void setCombat(GameState s, CombatState c) {
        com.shardbound.engine.TestCombat.setCombat(s, c);
    }

    @Test
    void startBuildsFreshEnemiesAndRefillsEnergy() {          // D20, S2
        GameState s = stateIn(engine, "rats");
        assertEquals(3, s.stats().energy());
        List<Enemy> first = s.combat().enemiesView();
        assertEquals(List.of("Rat A", "Rat B"), first.stream().map(Enemy::name).toList());
        CombatState second = engine.start(s, "rats");
        assertNotSame(first.get(0), second.enemiesView().get(0), "a new fight gets new Enemy objects");
    }

    @Test
    void demoModeScalesHpAndAtk() {
        Enemy normal = Enemy.from(SLOW, 0, "Slug", false);
        Enemy demo = Enemy.from(new EnemyTemplate("x", "X", 50, 10, 0, 0, 0, 0, "AGGRESSIVE"), 0, "X", true);
        assertEquals(50, normal.maxHp());
        assertEquals(30, demo.maxHp());
        assertEquals(6, demo.atk());
    }

    @Test
    void turnOrderIsFastestFirstPlayerWinsTies() {             // S1
        GameState s = stateIn(engine, "rats");
        PriorityQueue<Combatant> queue = new PriorityQueue<>(CombatEngine.TURN_ORDER);
        Enemy tie = Enemy.from(new EnemyTemplate("t", "Tie", 5, 1, 0, 5, 0, 0, "AGGRESSIVE"), 3, "Tie", false);
        Enemy fast = Enemy.from(FAST, 4, "Hare", false);
        queue.add(tie);
        queue.add(s.combat().player());       // spd 5, same as Tie
        queue.add(fast);
        assertSame(fast, queue.poll());
        assertTrue(queue.poll().isPlayer());
        assertSame(tie, queue.poll());
    }

    @Test
    void winningGrantsXpAndGold() {
        GameState s = stateIn(engine, "rats");
        int gold = s.stats().gold();
        assertEquals(CombatOutcome.ONGOING, engine.act(s, new Action(ActionType.ATTACK, 0, null)));
        assertEquals(CombatOutcome.WON, engine.act(s, new Action(ActionType.ATTACK, 1, null)));
        assertEquals(gold + 14, s.stats().gold());
        assertEquals(50, s.stats().xp());
    }

    @Test
    void losingCostsThirtyPercentGoldAndHalfHp() {
        GameState s = stateIn(engine, "brute");
        s.stats().addGold(85);                                  // 100 gold
        assertEquals(CombatOutcome.LOST, engine.act(s, Action.of(ActionType.DEFEND)));
        assertEquals(70, s.stats().gold());
        assertEquals(20, s.stats().hp());
    }

    @Test
    void skillCostsOneEnergy() {
        GameState s = stateIn(engine, "slow");
        engine.act(s, new Action(ActionType.SKILL, 0, null));
        assertEquals(2, s.stats().energy());
    }

    @Test
    void itemActionUsesAConsumable() {
        GameState s = stateIn(engine, "slow");
        s.stats().setHp(10);
        s.inventory().add(new Consumable("potion", "Potion", "", 0, 15, 0), 1);
        engine.act(s, new Action(ActionType.ITEM, 0, "potion"));
        assertEquals(0, s.inventory().count("potion"));
        assertTrue(s.stats().hp() >= 24, "healed 15 then hit for at most 1");
    }

    @Test
    void fleeChanceIsClamped() {
        assertEquals(75, engine.fleeChance(stateIn(engine, "slow").combat()));   // 50 + (5-0)*5
        assertEquals(10, engine.fleeChance(stateIn(engine, "fast").combat()));   // 50 + (5-20)*5 = -25 → 10
    }

    @Test
    void logKeepsOnlyTheLastEightLines() {
        GameState s = stateIn(engine, "slow");
        for (int i = 0; i < 10; i++) {
            engine.act(s, Action.of(ActionType.DEFEND));
        }
        assertEquals(CombatState.LOG_SIZE, s.combat().logView().size());
    }
}
