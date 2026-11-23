package com.shardbound.balance;

import com.shardbound.battle.Affinity;
import com.shardbound.battle.BattleAction;
import com.shardbound.battle.BattleContent;
import com.shardbound.battle.BattleEngine;
import com.shardbound.battle.BattleOutcome;
import com.shardbound.battle.BattleRules;
import com.shardbound.battle.BattleState;
import com.shardbound.battle.CreepTemplate;
import com.shardbound.battle.Element;
import com.shardbound.battle.Skill;
import com.shardbound.content.GameContent;
import com.shardbound.engine.GameState;
import com.shardbound.engine.TestBattles;
import com.shardbound.engine.TestEngines;
import com.shardbound.engine.TestStates;
import com.shardbound.progression.Attribute;
import com.shardbound.progression.LevelUp;
import com.shardbound.world.CreepSpawn;
import com.shardbound.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eldmere played start to boss by the balance bot over 1,000 seeds, with the
 * real battle, level-up, rest and flask rules (V2_SPEC §10.2, P-61 … P-64):
 * <ol>
 *   <li>the storm night: the Mire Slime and the Storm Rat in the meadow;</li>
 *   <li>the Heartshard; kindle and rest at the Junction Lantern;</li>
 *   <li>dawn: Ember from Brann; the Furrow Mole and the Hedge Adder on Mill Road; rest, level up;</li>
 *   <li>the Hired Blade;</li>
 *   <li>the ruins: the Ash Wisp and the Barrow Skull; rest, level up;</li>
 *   <li>one refight in the meadow; rest, level up: the state the boss meets.</li>
 * </ol>
 * The bot drinks below 40 % HP, uses a skill the enemy is weak to (or one it
 * does not resist when it resists Strike) when it has the FP, else Strikes, and
 * buys every level it can afford in a fixed attribute order. A lost fight sends
 * it back to the Lantern to rest and pick its Shards up again, then it retries.
 */
class EldmereProgressionTest {

    private static final int RUNS = 1000;
    private static final String DRAUGHT = "lantern_draught";
    private static final List<Attribute> BUILD = List.of(Attribute.VIGOR, Attribute.STRENGTH, Attribute.ENDURANCE,
            Attribute.DEXTERITY, Attribute.VIGOR, Attribute.MIND);
    /** The boss stand-in: the Hired Blade at the Ashen Herald's level (§10.1) with this much more HP. */
    private static final int BOSS_LEVEL = 5;
    private static final double BOSS_HP_MULTIPLIER = 1.8;

    private final GameContent content = TestEngines.content();
    private final World world = content.world();
    private final LevelUp levelUp = new LevelUp(content.progression());

    /** One seeded run of the route: what it saw on the way, and the state the boss meets. */
    private record Run(int shardsAfterTwo, int shardsAfterThree, int levelBeforeBoss, int deaths,
                       Map<String, List<Integer>> turns, GameState state) { }

    private record Spot(String area, String spawn) { }

    private static final List<Spot> STORM_NIGHT = List.of(
            new Spot("eldmere_cottage_lane", "meadow_creep_b"), new Spot("eldmere_cottage_lane", "meadow_creep_a"));
    private static final List<Spot> MILL_ROAD = List.of(
            new Spot("eldmere_mill_road", "field_creep_a"), new Spot("eldmere_mill_road", "field_creep_b"));
    private static final List<Spot> RUINS = List.of(
            new Spot("eldmere_old_ruins", "ruins_creep_a"), new Spot("eldmere_old_ruins", "ruins_creep_b"));

    private GameState newPlayer(long seed) {
        GameState s = TestStates.fresh(seed, false);
        World.Kit kit = world.start().kit();
        s.inventory().add(content.items().get(kit.weapon()), 1);
        s.inventory().equip(kit.weapon());
        kit.bag().forEach((item, qty) -> s.inventory().add(content.items().get(item), qty));
        kit.skills().forEach(s::learnSkill);
        s.refillFlasks();
        return s;
    }

    private Run play(long seed, BattleEngine engine) {
        GameState s = newPlayer(seed);
        Map<String, List<Integer>> turns = new TreeMap<>();
        int[] deaths = {0};
        fightAll(s, engine, STORM_NIGHT, turns, deaths);
        int afterTwo = s.stats().shards();
        rest(s);                                                    // kindled: 70 Shards buy nothing yet
        s.learnSkill("ember");                                      // dawn: Brann
        fightAll(s, engine, MILL_ROAD.subList(0, 1), turns, deaths);
        int afterThree = s.stats().shards();
        fightAll(s, engine, MILL_ROAD.subList(1, 2), turns, deaths);
        restAndLevel(s);
        fight(s, engine, content.battle().creeps().get("hired_blade"), 2, turns, deaths);
        fightAll(s, engine, RUINS, turns, deaths);
        restAndLevel(s);
        fightAll(s, engine, STORM_NIGHT.subList(0, 1), turns, deaths);   // one refight after the rest
        restAndLevel(s);
        return new Run(afterTwo, afterThree, s.stats().level(), deaths[0], turns, s);
    }

    private void fightAll(GameState s, BattleEngine engine, List<Spot> spots, Map<String, List<Integer>> turns,
                          int[] deaths) {
        for (Spot spot : spots) {
            CreepSpawn spawn = CreepSpawn.of(world.mapOf(spot.area()).object(spot.spawn()).orElseThrow());
            int level = spawn.levelMin() + s.rng().nextInt(spawn.levelMax() - spawn.levelMin() + 1);
            fight(s, engine, content.battle().creeps().get(spawn.template()), level, turns, deaths);
        }
    }

    /** Fights until won: a loss rests at the Lantern, picks the Shards back up, and tries again. */
    private void fight(GameState s, BattleEngine engine, CreepTemplate creep, int level,
                       Map<String, List<Integer>> turns, int[] deaths) {
        for (int attempt = 0; attempt < 5; attempt++) {
            int shards = s.stats().shards();
            BattleState b = engine.start(s, creep, level, null, "route", false);
            TestBattles.begin(s, b);
            int rounds = 0;
            while (b.outcome() == BattleOutcome.ONGOING && rounds < 60) {
                engine.act(s, choose(s, creep, engine));
                rounds++;
            }
            if (b.outcome() == BattleOutcome.WON) {
                turns.computeIfAbsent(creep.id(), k -> new ArrayList<>()).add(rounds);
                return;
            }
            deaths[0]++;
            rest(s);
            s.stats().addShards(shards - s.stats().shards());       // walked back to the Lost Shards
        }
        throw new AssertionError(creep.id() + " Lv " + level + " was never beaten");
    }

    private BattleAction choose(GameState s, CreepTemplate creep, BattleEngine engine) {
        if (s.stats().hp() * 100 < s.stats().maxHp() * 40 && s.flaskCharges(DRAUGHT) > 0) {
            return BattleAction.item(DRAUGHT);
        }
        BattleContent battle = content.battle();
        boolean strikeResisted = battle.chart().affinity(creep.family(), Element.PHYSICAL) == Affinity.R;
        int fallback = -1;
        for (int slot = 0; slot < s.loadout().size(); slot++) {
            Skill skill = s.loadout().get(slot) == null ? null : battle.skills().get(s.loadout().get(slot));
            if (skill == null || s.stats().fp() < skill.fpCost()) {
                continue;
            }
            Affinity affinity = battle.chart().affinity(creep.family(), skill.element());
            if (affinity == Affinity.W) {
                return BattleAction.skill(slot);
            }
            if (strikeResisted && affinity == Affinity.N && fallback < 0) {
                fallback = slot;
            }
        }
        return fallback >= 0 ? BattleAction.skill(fallback) : BattleAction.of(BattleAction.Kind.STRIKE);
    }

    private static void rest(GameState s) {
        s.stats().restoreAll();
        s.refillFlasks();
    }

    /** Rests, then buys every level it can afford, one attribute at a time in the build order. */
    private void restAndLevel(GameState s) {
        rest(s);
        for (int i = 0; ; i++) {
            Attribute next = BUILD.get(s.stats().level() % BUILD.size());
            LevelUp.Plan plan = levelUp.plan(s.stats().attributes(), Map.of(next, 1), s.stats().shards());
            if (!plan.affordable()) {
                return;
            }
            s.stats().applyLevelUp(plan);
        }
    }

    private List<Run> runs(BattleEngine engine) {
        List<Run> runs = new ArrayList<>();
        for (long seed = 1; seed <= RUNS; seed++) {
            runs.add(play(seed, engine));
        }
        return runs;
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    private BattleEngine engine(int victoryFpPct) {
        BattleContent b = content.battle();
        BattleRules r = b.rules();
        BattleRules rules = new BattleRules(r.hpPerLevel(), r.atkPerLevel(), r.defPerLevel(), r.shardsBase(),
                r.shardsExponent(), r.rollMin(), r.rollMax(), r.critMultiplier(), r.enemyCritPct(), r.guardNormal(),
                r.guardTelegraphed(), r.statusChanceCap(), r.fleeBasePct(), r.fleePctPerSpeed(), r.fleeMinPct(),
                r.fleeMaxPct(), r.fleeIgnoreTiles(), r.defensiveGuardBelowPct(), r.dialogueLossHpPct(), victoryFpPct);
        return new BattleEngine(new BattleContent(b.chart(), b.statuses(), b.skills(), b.creeps(), rules),
                content.items());
    }

    @Test
    void theFirstLevelUpComesAfterTheThirdFight() {
        for (Run run : runs(new BattleEngine(content.battle(), content.items()))) {
            assertTrue(run.shardsAfterTwo() < levelUp.cost(1), "after two fights: " + run.shardsAfterTwo());
            assertTrue(run.shardsAfterThree() >= levelUp.cost(1), "after three fights: " + run.shardsAfterThree());
        }
    }

    @Test
    void eldmereEndsAtLevelFourAndEveryCreepFightTakesThreeToSixTurns() {
        List<Run> runs = runs(new BattleEngine(content.battle(), content.items()));
        int level = median(runs.stream().map(Run::levelBeforeBoss).toList());
        int deaths = runs.stream().mapToInt(Run::deaths).sum();
        System.out.println("Eldmere: median level before the boss " + level + ", deaths per run "
                + String.format("%.2f", deaths / (double) RUNS));
        assertEquals(4, level, "§10.2: level 4 at the Ashen Herald");
        Map<String, List<Integer>> turns = new TreeMap<>();
        runs.forEach(run -> run.turns().forEach((creep, list) -> turns.computeIfAbsent(creep, k -> new ArrayList<>())
                .addAll(list)));
        turns.forEach((creep, list) -> {
            int median = median(list);
            System.out.println("  " + creep + ": median " + median + " turns over " + list.size() + " fights");
            if (!creep.equals("hired_blade")) {
                assertTrue(median >= 3 && median <= 6, creep + " median " + median + " turns");
            }
        });
        assertTrue(deaths * 100 <= RUNS * 25, "a death in at most one run in four on the way: " + deaths);
    }

    @Test
    void theBossStandInIsWonSeventyToEightyFivePercentOnTheFirstAttempt() {
        CreepTemplate blade = content.battle().creeps().get("hired_blade");
        CreepTemplate standIn = new CreepTemplate("boss_stand_in", "Boss stand-in", blade.family(), blade.sprite(),
                (int) Math.round(blade.baseHp() * BOSS_HP_MULTIPLIER), blade.baseAtk(), blade.baseDef(), blade.baseSpd(),
                blade.ai(), blade.moves(), List.of());
        BattleEngine engine = new BattleEngine(content.battle(), content.items());
        int wins = 0;
        for (Run run : runs(engine)) {
            GameState s = run.state();
            BattleState b = engine.start(s, standIn, BOSS_LEVEL, null, "boss", false);
            TestBattles.begin(s, b);
            for (int rounds = 0; b.outcome() == BattleOutcome.ONGOING && rounds < 80; rounds++) {
                engine.act(s, choose(s, standIn, engine));
            }
            if (b.outcome() == BattleOutcome.WON) {
                wins++;
            }
        }
        String report = "boss stand-in (Hired Blade Lv " + BOSS_LEVEL + ", HP ×" + BOSS_HP_MULTIPLIER + "): "
                + wins * 100 / RUNS + " % first-attempt wins";
        System.out.println(report);
        assertTrue(wins * 100 >= RUNS * 70 && wins * 100 <= RUNS * 85, report);
    }

    @Test
    void reportsTheFpAfterVictoryVariants() {
        for (int pct : new int[] {0, 25, 100}) {
            List<Run> runs = runs(engine(pct));
            Map<String, List<Integer>> turns = new TreeMap<>();
            runs.forEach(run -> run.turns().forEach((creep, list) -> turns.computeIfAbsent(creep, k -> new ArrayList<>())
                    .addAll(list)));
            int deaths = runs.stream().mapToInt(Run::deaths).sum();
            StringBuilder line = new StringBuilder("FP after victory " + pct + " %: deaths per run "
                    + String.format("%.3f", deaths / (double) RUNS) + "; mean turns:");
            turns.forEach((creep, list) -> line.append(' ').append(creep).append(' ')
                    .append(String.format("%.2f", list.stream().mapToInt(Integer::intValue).average().orElse(0))));
            System.out.println(line);
        }
    }
}
