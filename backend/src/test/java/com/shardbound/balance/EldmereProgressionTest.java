package com.shardbound.balance;

import com.shardbound.battle.Affinity;
import com.shardbound.battle.BattleAction;
import com.shardbound.battle.BattleContent;
import com.shardbound.battle.BattleEngine;
import com.shardbound.battle.BattleOutcome;
import com.shardbound.battle.BattleRules;
import com.shardbound.battle.BattleState;
import com.shardbound.battle.BossTemplate;
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
import java.util.Comparator;
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
 * <p>Gear (P-65): at dawn the bot opens the ruins chest on the main path and
 * equips the Woodcutter's Hatchet; the rusty-sword route (a player who skipped
 * the chest) is reported, and a third route also detours to the secret
 * clearing for the Hunter's Longbow, with a Dexterity build. After every rest
 * the bot holds whichever weapon the server rates higher, as the Bag's Compare
 * shows it.
 * <p>Then the Ashen Herald itself (P-67), from {@code bosses.json}, met by two
 * policies: the guarding bot Guards while a telegraphed blow is pending (what
 * the battle teaches; Guard goes first, P-69) and otherwise fights as above;
 * the naive bot never Guards and drinks below 35 % HP. A loss wakes the bot at
 * the Ruins Lantern, rested, with its Shards picked up again, and it tries
 * again: the attempts per run are V2_SPEC §10.3's difficulty curve.
 */
class EldmereProgressionTest {

    private static final int RUNS = 1000;
    private static final String DRAUGHT = "lantern_draught";
    /** The build order, one raise per level from level 1: the Vigor and Strength route (§10.2). */
    private static final List<Attribute> BUILD = List.of(Attribute.VIGOR, Attribute.STRENGTH, Attribute.ENDURANCE,
            Attribute.DEXTERITY, Attribute.VIGOR, Attribute.MIND);
    /** The Dexterity route for the secret longbow (P-65): Dexterity first. */
    private static final List<Attribute> DEX_BUILD = List.of(Attribute.VIGOR, Attribute.DEXTERITY, Attribute.DEXTERITY,
            Attribute.VIGOR, Attribute.ENDURANCE, Attribute.MIND);
    private static final String HERALD = "ashen_herald";
    /** Fights at most per run against the Herald before the run counts as stuck. */
    private static final int MAX_ATTEMPTS = 6;

    /** What the bot carries: the starting sword only, the main-path hatchet, or the hatchet and the secret longbow. */
    private enum Gear {
        RUSTY(List.of()), HATCHET(List.of("woodcutter_hatchet")), HATCHET_AND_LONGBOW(List.of("woodcutter_hatchet", "hunter_longbow"));

        private final List<String> chestWeapons;

        Gear(List<String> chestWeapons) {
            this.chestWeapons = chestWeapons;
        }
    }

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

    private Run play(long seed, BattleEngine engine, Gear gear, List<Attribute> build) {
        GameState s = newPlayer(seed);
        Map<String, List<Integer>> turns = new TreeMap<>();
        int[] deaths = {0};
        fightAll(s, engine, STORM_NIGHT, turns, deaths);
        int afterTwo = s.stats().shards();
        gear.chestWeapons.forEach(id -> s.inventory().add(content.items().get(id), 1));   // dawn: the chests
        rest(s);                                                    // kindled: 70 Shards buy nothing yet
        s.learnSkill("ember");                                      // dawn: Brann
        fightAll(s, engine, MILL_ROAD.subList(0, 1), turns, deaths);
        int afterThree = s.stats().shards();
        fightAll(s, engine, MILL_ROAD.subList(1, 2), turns, deaths);
        restAndLevel(s, build);
        fight(s, engine, content.battle().creeps().get("hired_blade"), 2, turns, deaths);
        fightAll(s, engine, RUINS, turns, deaths);
        restAndLevel(s, build);
        fightAll(s, engine, STORM_NIGHT.subList(0, 1), turns, deaths);   // one refight after the rest
        restAndLevel(s, build);
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
        return choose(s, creep, 40);
    }

    /** A draught below `drinkBelowPct` % HP; else a skill the enemy is weak to (or one it does not resist when it resists Strike); else Strike. */
    private BattleAction choose(GameState s, CreepTemplate creep, int drinkBelowPct) {
        if (s.stats().hp() * 100 < s.stats().maxHp() * drinkBelowPct && s.flaskCharges(DRAUGHT) > 0) {
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

    /** Rests at the Lantern and, as the Bag's Compare would show it, holds the weapon with the higher attack rating. */
    private static void rest(GameState s) {
        s.stats().restoreAll();
        s.refillFlasks();
        s.inventory().weaponsByTier().stream()
                .max(Comparator.comparingDouble(w -> s.stats().derived().attackRating(w)))
                .ifPresent(best -> s.inventory().equip(best.id()));
    }

    /** Rests, buys every level it can afford one attribute at a time in the build order, then rests again. */
    private void restAndLevel(GameState s, List<Attribute> build) {
        rest(s);
        while (true) {
            Attribute next = build.get(s.stats().level() % build.size());
            LevelUp.Plan plan = levelUp.plan(s.stats().attributes(), Map.of(next, 1), s.stats().shards());
            if (!plan.affordable()) {
                break;
            }
            s.stats().applyLevelUp(plan);
        }
        rest(s);                                                    // the new attributes may favour another weapon
    }

    private List<Run> runs(BattleEngine engine, Gear gear) {
        return runs(engine, gear, BUILD);
    }

    private List<Run> runs(BattleEngine engine, Gear gear, List<Attribute> build) {
        List<Run> runs = new ArrayList<>();
        for (long seed = 1; seed <= RUNS; seed++) {
            runs.add(play(seed, engine, gear, build));
        }
        return runs;
    }

    private static Map<String, List<Integer>> turnsOf(List<Run> runs) {
        Map<String, List<Integer>> turns = new TreeMap<>();
        runs.forEach(run -> run.turns().forEach((creep, list) -> turns.computeIfAbsent(creep, k -> new ArrayList<>())
                .addAll(list)));
        return turns;
    }

    /** How the bot fights the Herald (P-67, P-69). */
    private enum Policy {
        /** Guards a pending telegraph (unless low: then it drinks), drinks below 40 %. */
        GUARDING(40),
        /** Never guards; drinks below 35 %. */
        NAIVE(35);

        private final int drinkBelowPct;

        Policy(int drinkBelowPct) {
            this.drinkBelowPct = drinkBelowPct;
        }
    }

    /**
     * How the Herald went for a run: the fights it took to win (0: never won in MAX_ATTEMPTS), and the first
     * fight's rounds and draughts drunk.
     */
    private record HeraldResult(int attempts, int firstTurns, int firstDraughts) {
        boolean firstAttempt() {
            return attempts == 1;
        }
    }

    /** Each run meets the Ashen Herald; a loss rests at the Ruins Lantern, picks the Shards back up and tries again. */
    private List<HeraldResult> meetTheHerald(List<Run> runs, BattleEngine engine, Policy policy) {
        return meetTheHerald(runs, engine, policy, content.battle().bosses().get(HERALD));
    }

    private List<HeraldResult> meetTheHerald(List<Run> runs, BattleEngine engine, Policy policy, BossTemplate herald) {
        List<HeraldResult> results = new ArrayList<>();
        for (Run run : runs) {
            GameState s = run.state();
            int won = 0;
            int firstTurns = 0;
            int firstDraughts = 0;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS && won == 0; attempt++) {
                int shards = s.stats().shards();
                BattleState b = engine.startBoss(s, herald);
                TestBattles.begin(s, b);
                int rounds = 0;
                int draughts = 0;
                for (; b.outcome() == BattleOutcome.ONGOING && rounds < 80; rounds++) {
                    BattleAction action = chooseAgainst(s, b, herald, policy);
                    if (action.kind() == BattleAction.Kind.ITEM) {
                        draughts++;
                    }
                    engine.act(s, action);
                }
                if (attempt == 1) {
                    firstTurns = rounds;
                    firstDraughts = draughts;
                }
                if (b.outcome() == BattleOutcome.WON) {
                    won = attempt;
                } else {
                    rest(s);
                    s.stats().addShards(shards - s.stats().shards());   // walked back to the Lost Shards
                }
            }
            results.add(new HeraldResult(won, firstTurns, firstDraughts));
        }
        return results;
    }

    /** The guarding bot Guards a pending telegraph ("Guard the fall", P-67) unless it must drink; otherwise as against a creep. */
    private BattleAction chooseAgainst(GameState s, BattleState b, BossTemplate boss, Policy policy) {
        boolean low = s.stats().hp() * 100 < s.stats().maxHp() * policy.drinkBelowPct && s.flaskCharges(DRAUGHT) > 0;
        if (policy == Policy.GUARDING && !low && !b.enemy().pendingTelegraphs().isEmpty()) {
            return BattleAction.of(BattleAction.Kind.GUARD);
        }
        return choose(s, boss.asCreep(), policy.drinkBelowPct);
    }

    private static int firstAttemptPct(List<HeraldResult> results) {
        return (int) (results.stream().filter(HeraldResult::firstAttempt).count() * 100 / results.size());
    }

    private static double attemptsPerRun(List<HeraldResult> results) {
        return results.stream().mapToInt(r -> r.attempts() == 0 ? MAX_ATTEMPTS : r.attempts()).average().orElse(0);
    }

    private static double creepDeathsPerRun(List<Run> runs) {
        return runs.stream().mapToInt(Run::deaths).sum() / (double) runs.size();
    }

    private static String report(String route, List<Run> runs, List<HeraldResult> results) {
        return route + ": the Ashen Herald won on the first attempt in " + firstAttemptPct(results) + " % of runs; "
                + String.format("%.2f", attemptsPerRun(results)) + " attempts per run; first fight median "
                + median(results.stream().map(HeraldResult::firstTurns).toList()) + " turns and "
                + median(results.stream().map(HeraldResult::firstDraughts).toList()) + " draughts; "
                + String.format("%.2f", creepDeathsPerRun(runs)) + " creep deaths per run; "
                + results.stream().filter(r -> r.attempts() == 0).count() + " runs never won";
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
                r.fleeMaxPct(), r.fleeIgnoreTiles(), r.defensiveGuardBelowPct(), r.dialogueLossHpPct(), victoryFpPct,
                r.bossShardsMultiplier());
        return new BattleEngine(new BattleContent(b.chart(), b.statuses(), b.skills(), b.creeps(), b.bosses(), rules,
                b.brace()),
                content.items());
    }

    @Test
    void theFirstLevelUpComesAfterTheThirdFight() {
        for (Run run : runs(new BattleEngine(content.battle(), content.items()), Gear.HATCHET)) {
            assertTrue(run.shardsAfterTwo() < levelUp.cost(1), "after two fights: " + run.shardsAfterTwo());
            assertTrue(run.shardsAfterThree() >= levelUp.cost(1), "after three fights: " + run.shardsAfterThree());
        }
    }

    @Test
    void eldmereEndsAtLevelFourAndEveryCreepFightTakesThreeToSixTurns() {
        List<Run> runs = runs(new BattleEngine(content.battle(), content.items()), Gear.HATCHET);
        int level = median(runs.stream().map(Run::levelBeforeBoss).toList());
        int deaths = runs.stream().mapToInt(Run::deaths).sum();
        System.out.println("Eldmere with the hatchet: median level before the boss " + level + ", deaths per run "
                + String.format("%.2f", deaths / (double) RUNS));
        assertEquals(4, level, "§10.2: level 4 at the Ashen Herald");
        turnsOf(runs).forEach((creep, list) -> {
            int median = median(list);
            System.out.println("  " + creep + ": median " + median + " turns over " + list.size() + " fights");
            if (!creep.equals("hired_blade")) {
                assertTrue(median >= 3 && median <= 6, creep + " median " + median + " turns");
            }
        });
        assertTrue(deaths * 100 <= RUNS * 25, "a death in at most one run in four on the way: " + deaths);
    }

    /**
     * §10.3, Eldmere's row (P-67, P-69): the main path, the hatchet on the Vigor and Strength route at level 4.
     * The guarding bot wins 70–85 % at the first attempt in about 8–12 turns; a naive one that never guards
     * roughly 35–50 %, so the lesson matters.
     */
    @Test
    void theHeraldIsWonSeventyToEightyFivePercentWithTheHatchetByGuardingItsTelegraphs() {
        BattleEngine engine = new BattleEngine(content.battle(), content.items());
        List<Run> runs = runs(engine, Gear.HATCHET);
        List<HeraldResult> results = meetTheHerald(runs, engine, Policy.GUARDING);
        String report = report("hatchet, Vigor and Strength, guarding", runs, results);
        System.out.println(report);
        int pct = firstAttemptPct(results);
        assertTrue(pct >= 70 && pct <= 85, report);
        assertTrue(results.stream().allMatch(r -> r.attempts() > 0), "every run beats the Herald in time: " + report);
        int turns = median(results.stream().map(HeraldResult::firstTurns).toList());
        assertTrue(turns >= 8 && turns <= 12, "median fight length: " + report);

        List<Run> naiveRuns = runs(engine, Gear.HATCHET);
        List<HeraldResult> naive = meetTheHerald(naiveRuns, engine, Policy.NAIVE);
        String naiveReport = report("hatchet, Vigor and Strength, never guarding", naiveRuns, naive);
        System.out.println(naiveReport);
        int naivePct = firstAttemptPct(naive);
        assertTrue(naivePct >= 35 && naivePct <= 50, naiveReport);
    }

    /** Reported for P-67 and §10.3: the secret longbow on a Dexterity build, and a player who skipped the chest. */
    @Test
    void reportsTheHeraldWithTheLongbowAndWithTheRustySword() {
        BattleEngine engine = new BattleEngine(content.battle(), content.items());
        List<Run> longbow = runs(engine, Gear.HATCHET_AND_LONGBOW, DEX_BUILD);
        List<HeraldResult> withLongbow = meetTheHerald(longbow, engine, Policy.GUARDING);
        long held = longbow.stream().filter(r -> "hunter_longbow".equals(r.state().inventory().equippedWeaponId())).count();
        System.out.println(report("longbow, Dexterity (longbow held in " + held + " runs)", longbow, withLongbow));
        List<Run> rusty = runs(engine, Gear.RUSTY);
        System.out.println(report("Rusty Shortsword only", rusty, meetTheHerald(rusty, engine, Policy.GUARDING)));
        assertTrue(firstAttemptPct(withLongbow) <= 90, "the secret longbow must not trivialise the Herald");
        turnsOf(longbow).forEach((creep, list) -> assertTrue(creep.equals("hired_blade") || median(list) >= 3,
                "the secret longbow leaves " + creep + " at a median of " + median(list) + " turns"));
    }

    @Test
    void reportsTheFpAfterVictoryVariants() {
        for (int pct : new int[] {0, 25, 100}) {
            List<Run> runs = runs(engine(pct), Gear.HATCHET);
            Map<String, List<Integer>> turns = turnsOf(runs);
            int deaths = runs.stream().mapToInt(Run::deaths).sum();
            StringBuilder line = new StringBuilder("FP after victory " + pct + " %: deaths per run "
                    + String.format("%.3f", deaths / (double) RUNS) + "; mean turns:");
            turns.forEach((creep, list) -> line.append(' ').append(creep).append(' ')
                    .append(String.format("%.2f", list.stream().mapToInt(Integer::intValue).average().orElse(0))));
            System.out.println(line);
        }
    }
}
