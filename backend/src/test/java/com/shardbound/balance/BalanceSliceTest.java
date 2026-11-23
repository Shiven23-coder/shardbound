package com.shardbound.balance;

import com.shardbound.battle.Affinity;
import com.shardbound.battle.BattleAction;
import com.shardbound.battle.BattleContent;
import com.shardbound.battle.BattleEngine;
import com.shardbound.battle.BattleOutcome;
import com.shardbound.battle.BattleState;
import com.shardbound.battle.CreepTemplate;
import com.shardbound.battle.Skill;
import com.shardbound.content.GameContent;
import com.shardbound.engine.GameState;
import com.shardbound.engine.TestBattles;
import com.shardbound.engine.TestEngines;
import com.shardbound.engine.TestStates;
import com.shardbound.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The V4 balance slice: a seeded, headless battle runner playing the bot
 * policy of V2_SPEC §4.14 ("weakness skill > Strike; draught when HP &lt; 40 %")
 * with the starting kit (P-55). Proves that each meadow creep at its levels
 * takes 3–6 player turns and is won ≥ 95 %, and that the Hired Blade is
 * winnable at level 1: ≥ 90 % fresh, ≥ 85 % after both meadow creeps with the
 * HP and items left over.
 */
class BalanceSliceTest {

    private static final int RUNS = 1000;
    private static final String DRAUGHT = "lantern_draught";

    private final GameContent content = TestEngines.content();
    private final BattleContent battle = content.battle();
    private final BattleEngine engine = new BattleEngine(battle, content.items());

    /** One player turn per round; the battle's result and how many rounds it took. */
    private record Result(BattleOutcome outcome, int rounds) { }

    private GameState freshPlayer(long seed) {
        GameState s = TestStates.fresh(seed, false);
        World.Kit kit = content.world().start().kit();
        s.inventory().add(content.items().get(kit.weapon()), 1);
        s.inventory().equip(kit.weapon());
        kit.bag().forEach((item, qty) -> s.inventory().add(content.items().get(item), qty));
        kit.skills().forEach(s::learnSkill);
        s.refillFlasks();                         // a new game starts with the Lantern Draught full (P-63)
        return s;
    }

    private Result fight(GameState s, String creepId, int level) {
        CreepTemplate creep = battle.creeps().get(creepId);
        BattleState b = engine.start(s, creep, level, null, "slice", false);
        TestBattles.begin(s, b);
        int rounds = 0;
        while (b.outcome() == BattleOutcome.ONGOING && rounds < 50) {
            engine.act(s, choose(s, creep));
            rounds++;
        }
        return new Result(b.outcome(), rounds);
    }

    /** The bot policy: a draught below 40 % HP, else a skill the enemy is weak to, else Strike. */
    private BattleAction choose(GameState s, CreepTemplate creep) {
        if (s.stats().hp() * 100 < s.stats().maxHp() * 40 && s.flaskCharges(DRAUGHT) > 0) {
            return BattleAction.item(DRAUGHT);
        }
        List<String> loadout = s.loadout();
        for (int slot = 0; slot < loadout.size(); slot++) {
            Skill skill = loadout.get(slot) == null ? null : battle.skills().get(loadout.get(slot));
            if (skill != null && s.stats().fp() >= skill.fpCost()
                    && battle.chart().affinity(creep.family(), skill.element()) == Affinity.W) {
                return BattleAction.skill(slot);
            }
        }
        return BattleAction.of(BattleAction.Kind.STRIKE);
    }

    private void assertCreep(String creepId, int level) {
        List<Integer> rounds = new ArrayList<>();
        int wins = 0;
        for (long seed = 1; seed <= RUNS; seed++) {
            Result r = fight(freshPlayer(seed), creepId, level);
            if (r.outcome() == BattleOutcome.WON) {
                wins++;
                rounds.add(r.rounds());
            }
        }
        Collections.sort(rounds);
        int median = rounds.get(rounds.size() / 2);
        String report = creepId + " Lv " + level + ": " + wins * 100 / RUNS + " % won, median " + median + " turns";
        System.out.println(report);
        assertTrue(wins * 100 >= RUNS * 95, report);
        assertTrue(median >= 3 && median <= 6, report);
    }

    @Test
    void theMeadowCreepsTakeThreeToSixTurnsAtLevelOne() {
        assertCreep("mire_slime", 1);                 // P-64: both meadow creeps at level 1
        assertCreep("storm_rat", 1);
    }

    @Test
    void theHiredBladeIsWinnableAtLevelOneWithTheStartingWeapon() {
        int wins = 0;
        for (long seed = 1; seed <= RUNS; seed++) {
            if (fight(freshPlayer(seed), "hired_blade", 2).outcome() == BattleOutcome.WON) {
                wins++;
            }
        }
        String report = "hired_blade fresh: " + wins * 100 / RUNS + " % won";
        System.out.println(report);
        assertTrue(wins * 100 >= RUNS * 90, report);
    }

    @Test
    void theHiredBladeIsWinnableAfterBothMeadowCreeps() {
        int wins = 0;
        for (long seed = 1; seed <= RUNS; seed++) {
            GameState s = freshPlayer(seed);
            if (fight(s, "mire_slime", 1).outcome() != BattleOutcome.WON
                    || fight(s, "storm_rat", 1).outcome() != BattleOutcome.WON) {
                continue;                         // a lost meadow fight sends the player home to heal
            }
            if (fight(s, "hired_blade", 2).outcome() == BattleOutcome.WON) {
                wins++;
            }
        }
        String report = "hired_blade after the meadow, no rest: " + wins * 100 / RUNS + " % won";
        System.out.println(report);
        assertTrue(wins * 100 >= RUNS * 85, report);
    }
}
