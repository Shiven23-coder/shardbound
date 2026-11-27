package com.shardbound.battle;

import com.shardbound.battle.ai.EnemyAI;
import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.exception.InventoryFullException;
import com.shardbound.item.Consumable;
import com.shardbound.item.Flask;
import com.shardbound.item.Item;
import com.shardbound.rules.Rewards;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Turn-based battle rules (V2_SPEC §4.5–4.7, P-54). Knows nothing about the
 * map or the story: {@link #act} returns a {@link BattleOutcome} and the game
 * engine decides what happens next (D21). Shared by all sessions; holds only
 * immutable content.
 */
public final class BattleEngine {

    private final BattleContent content;
    private final Map<String, Item> items;
    private final DamageCalculator calculator;

    /** @param items every item, for drops */
    public BattleEngine(BattleContent content, Map<String, Item> items) {
        this.content = content;
        this.items = items;
        this.calculator = new DamageCalculator(content);
    }

    public BattleContent content() {
        return content;
    }

    /** Starts a battle against {@code template} at {@code level}: a fresh fighter (D20) and an opening line. */
    public BattleState start(GameState s, CreepTemplate template, int level, @Nullable String creepRef,
                             @Nullable String encounterId, boolean canFlee) {
        BattleState battle = build(s, template, level, creepRef, encounterId, canFlee);
        battle.startRound();
        battle.say("A " + template.name() + " (Lv " + level + ") stands in your way!");
        return battle;
    }

    /** Starts a boss battle (V2_SPEC §4.7, P-67): the boss at its own level, and no fleeing. */
    public BattleState startBoss(GameState s, BossTemplate boss) {
        BattleState battle = buildBoss(s, boss);
        battle.startRound();
        battle.say("The " + boss.name() + " stands before you!");
        return battle;
    }

    private BattleState buildBoss(GameState s, BossTemplate boss) {
        PlayerFighter player = new PlayerFighter(s.stats(), s.inventory().equippedWeapon());
        return new BattleState(player, new CreepFighter(boss, content.rules()), null, null, false);
    }

    /** Rebuilds a stored battle (P-22): the same fighters, then the stored HP, statuses and round on top. */
    public BattleState restore(GameState s, BattleSnapshot snapshot) {
        BossTemplate boss = content.bosses().get(snapshot.creepId());
        CreepTemplate template = content.creeps().get(snapshot.creepId());
        if (template == null && boss == null) {
            throw new IllegalStateException("Stored battle against unknown enemy " + snapshot.creepId());
        }
        BattleState battle = boss != null ? buildBoss(s, boss)
                : build(s, template, snapshot.level(), snapshot.creepRef(), snapshot.encounterId(), snapshot.canFlee());
        CreepFighter enemy = battle.enemy();
        List<Skill> telegraphs = new ArrayList<>();
        for (String id : snapshot.enemyTelegraphs()) {
            telegraphs.add(skill(id));
        }
        enemy.restore(snapshot.enemyHp(), snapshot.enemyGuardedOnce(), telegraphs);
        enemy.setGuarding(snapshot.enemyGuarding());
        enemy.statuses().restore(snapshot.enemyStatuses(), snapshot.enemyImmunity());
        battle.player().setGuarding(snapshot.playerGuarding());
        battle.player().statuses().restore(snapshot.playerStatuses(), snapshot.playerImmunity());
        battle.restore(snapshot.round(), snapshot.text(), snapshot.hits());
        return battle;
    }

    private BattleState build(GameState s, CreepTemplate template, int level, @Nullable String creepRef,
                              @Nullable String encounterId, boolean canFlee) {
        PlayerFighter player = new PlayerFighter(s.stats(), s.inventory().equippedWeapon());
        CreepFighter enemy = new CreepFighter(template, level, content.rules());
        return new BattleState(player, enemy, creepRef, encounterId, canFlee);
    }

    /** The skill with this id; content was validated, so a miss is a bug. */
    public Skill skill(String id) {
        Skill skill = content.skills().get(id);
        if (skill == null) {
            throw new IllegalStateException("Unknown skill " + id);
        }
        return skill;
    }

    // =====================================================================
    // a round
    // =====================================================================

    /**
     * Plays one round (V2_SPEC §4.5, §4.7). Guard goes first, for every fighter
     * (P-69): whoever guards raises it before anyone acts, and it lasts to the
     * end of the round, so the hit it blunts is this round's. Then the other
     * actions, faster first (the player on ties). Each fighter's turn starts with
     * its statuses' tick; the order is rebuilt every round from current speed, so
     * Frost counts (P-54). A creep decides its action as the round begins, so its
     * Guard can go first too. The caller checked the action is allowed. O(1) per round.
     */
    public BattleOutcome act(GameState s, BattleAction action) {
        BattleState battle = s.battle();
        battle.startRound();
        PlayerFighter player = battle.player();
        CreepFighter enemy = battle.enemy();
        player.setGuarding(false);
        enemy.setGuarding(false);
        EnemyAI.Choice plan = planEnemy(s, battle);
        boolean playerGuards = action.kind() == BattleAction.Kind.GUARD;
        boolean enemyGuards = plan != null && plan.guards();
        for (Fighter fighter : guardsFirst(battle, playerGuards, enemyGuards)) {
            if (!fighter.isAlive()) {
                continue;
            }
            Turn turn = openTurn(s, battle, fighter);
            if (turn.over() != null) {
                return turn.over();
            }
            boolean guards = fighter.isPlayer() ? playerGuards : enemyGuards;
            if (turn.stunned()) {
                continue;
            }
            if (guards) {
                fighter.setGuarding(true);
                battle.say(fighter.isPlayer() ? "You raise your guard." : "The " + enemy.name() + " braces itself.");
                continue;
            }
            if (fighter.isPlayer()) {
                if (playerTurn(s, battle, action)) {
                    battle.say("You got away safely!");
                    battle.end(BattleOutcome.FLED, null);
                    return BattleOutcome.FLED;
                }
            } else {
                enemyTurn(s, battle, plan);
            }
            BattleOutcome after = outcomeOf(s, battle);
            if (after != BattleOutcome.ONGOING) {
                return after;
            }
        }
        return BattleOutcome.ONGOING;
    }

    /** How a fighter's turn opened: the battle's end if its statuses ended it, and whether it is stunned. */
    private record Turn(@Nullable BattleOutcome over, boolean stunned) { }

    /** A fighter's turn opens with its statuses' tick: a Stun costs the turn, and Burn or Poison may end the battle. */
    private Turn openTurn(GameState s, BattleState battle, Fighter fighter) {
        boolean skip = startOfTurn(battle, fighter);
        BattleOutcome after = outcomeOf(s, battle);
        if (after != BattleOutcome.ONGOING) {
            return new Turn(after, skip);
        }
        if (skip) {
            battle.say(subject(fighter) + (fighter.isPlayer() ? " are" : " is") + " stunned and cannot move!");
        }
        return new Turn(null, skip);
    }

    /**
     * The order of the round's turns: whoever guards first (the player before the enemy if both do, P-69),
     * then the rest by speed.
     */
    private List<Fighter> guardsFirst(BattleState battle, boolean playerGuards, boolean enemyGuards) {
        List<Fighter> bySpeed = turnOrder(battle);
        List<Fighter> result = new ArrayList<>();
        if (playerGuards) {
            result.add(battle.player());
        }
        if (enemyGuards) {
            result.add(battle.enemy());
        }
        bySpeed.stream().filter(f -> !result.contains(f)).forEach(result::add);
        return result;
    }

    /**
     * What a creep will do this round, decided as the round begins so a Guard can go first (P-69); null when
     * its announced move lands this round or it is a boss (whose cadence picks at its turn).
     */
    private EnemyAI.@Nullable Choice planEnemy(GameState s, BattleState battle) {
        CreepFighter enemy = battle.enemy();
        if (enemy.boss() != null || !enemy.pendingTelegraphs().isEmpty()) {
            return null;
        }
        return EnemyAI.forStrategy(enemy.template().ai()).decide(enemy, battle.player(), content.rules(), s.rng());
    }

    /** Faster first; the player wins ties. A {@code PriorityQueue}, as in v1 (V2_SPEC §4.7). */
    private List<Fighter> turnOrder(BattleState battle) {
        Comparator<Fighter> order = Comparator.<Fighter>comparingInt(f -> f.speed(content)).reversed()
                .thenComparing(f -> f.isPlayer() ? 0 : 1);
        PriorityQueue<Fighter> queue = new PriorityQueue<>(order);
        queue.add(battle.player());
        queue.add(battle.enemy());
        List<Fighter> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            result.add(queue.poll());
        }
        return result;
    }

    /** Status damage at the start of a fighter's turn; returns true if it loses the turn. */
    private boolean startOfTurn(BattleState battle, Fighter fighter) {
        StatusTracker.Tick tick = fighter.statuses().tick(fighter.maxHp(), content.statuses());
        for (Map.Entry<Status, Integer> damage : tick.damage()) {
            int phase = phaseOf(fighter);
            int lost = fighter.takeDamage(damage.getValue());
            battle.record(new HitLine(fighter.isPlayer(), lost, false, null, damage.getKey(), null, battle.nextLine()));
            battle.say(subject(fighter) + (fighter.isPlayer() ? " suffer " : " suffers ") + lost + " from "
                    + content.status(damage.getKey()).name().toLowerCase() + ".");
            checkPhase(battle, fighter, phase);
        }
        if (tick.skipTurn() && isBoss(fighter)) {
            fighter.statuses().grantImmunity(Status.STUN, BOSS_STUN_IMMUNE_TURNS);   // §4.5
        }
        return tick.skipTurn();
    }

    /** A boss is Stun-immune this many turns after a Stun (V2_SPEC §4.5). */
    private static final int BOSS_STUN_IMMUNE_TURNS = 3;

    private static boolean isBoss(Fighter fighter) {
        return fighter instanceof CreepFighter creep && creep.boss() != null;
    }

    private static int phaseOf(Fighter fighter) {
        return fighter instanceof CreepFighter creep ? creep.phase() : 1;
    }

    /** A boss that just fell to its second phase says its line and cannot be stunned until its next turn (§4.7). */
    private void checkPhase(BattleState battle, Fighter fighter, int phaseBefore) {
        if (fighter instanceof CreepFighter creep && creep.boss() != null && creep.isAlive()
                && phaseBefore == 1 && creep.phase() == 2) {
            battle.say(creep.bossPhase().line());
            battle.markPhaseLine();
            creep.statuses().grantImmunity(Status.STUN, 1);
        }
    }

    /** Performs the player's action; returns true if the player escaped. */
    private boolean playerTurn(GameState s, BattleState battle, BattleAction action) {
        PlayerFighter player = battle.player();
        switch (action.kind()) {
            case STRIKE -> strike(s, battle, player, battle.enemy(), Skill.STRIKE, false);
            case GUARD -> throw new IllegalStateException("Guard goes first, before the turns (P-69)");
            case SKILL -> {
                Skill skill = skill(s.loadout().get(action.slot()));
                player.stats().spendFp(skill.fpCost());
                strike(s, battle, player, battle.enemy(), skill, false);
            }
            case ITEM -> useItem(s, battle, action.itemId());
            case FLEE -> {
                return tryFlee(s, battle);
            }
        }
        return false;
    }

    private void useItem(GameState s, BattleState battle, @Nullable String itemId) {
        Item item = itemId == null ? null : s.inventory().get(itemId);
        if (item instanceof Flask flask) {
            int healed = Rewards.useFlask(s, s.bus(), flask);
            battle.say("You drink the " + flask.name() + (healed > 0 ? " and recover " + healed + " HP" : "") + ".");
        } else if (item instanceof Consumable consumable) {
            int hpBefore = s.stats().hp();
            int fpBefore = s.stats().fp();
            Rewards.useConsumable(s, s.bus(), consumable);
            int healed = s.stats().hp() - hpBefore;
            int restored = s.stats().fp() - fpBefore;
            battle.say("You use " + consumable.name() + (healed > 0 ? " and recover " + healed + " HP" : "")
                    + (restored > 0 ? (healed > 0 ? " and " : " and recover ") + restored + " FP" : "") + ".");
        }
    }

    /** Flee chance = base + (player speed − enemy speed) · step, clamped (v1 §6.6 with V2 speeds, P-54). */
    public int fleeChance(BattleState battle) {
        BattleRules rules = content.rules();
        int diff = battle.player().speed(content) - battle.enemy().speed(content);
        int chance = rules.fleeBasePct() + diff * rules.fleePctPerSpeed();
        return Math.max(rules.fleeMinPct(), Math.min(rules.fleeMaxPct(), chance));
    }

    private boolean tryFlee(GameState s, BattleState battle) {
        if (s.rng().nextInt(100) < fleeChance(battle)) {
            return true;
        }
        battle.say("You try to run, but the " + battle.enemy().name() + " cuts you off!");
        return false;
    }

    /**
     * The enemy's turn: a move announced last turn lands now; a boss follows its
     * cadence; a creep does what it planned as the round began (its Guard has
     * already gone first), and a telegraphed move is only announced (P-54).
     */
    private void enemyTurn(GameState s, BattleState battle, EnemyAI.@Nullable Choice choice) {
        CreepFighter enemy = battle.enemy();
        Skill announced = enemy.nextTelegraph().orElse(null);
        if (announced != null) {
            strike(s, battle, enemy, battle.player(), announced, true);
            return;
        }
        if (enemy.boss() != null) {
            bossTurn(s, battle, enemy, s.rng());
            return;
        }
        EnemyAI.Choice planned = Objects.requireNonNull(choice, "a creep plans its action as the round begins");
        if (planned.move().telegraph()) {
            enemy.announce(planned.move());
            battle.say(planned.move().telegraphLine().replace("{name}", enemy.name()));
        } else {
            strike(s, battle, enemy, battle.player(), planned.move(), false);
        }
    }

    /**
     * A boss's turn (V2_SPEC §4.7, P-67): every n-th of its moves is the phase's telegraphed move, which takes two
     * of its turns (announced, then landed), so it is announced every (n + 1)-th round; otherwise it picks a move
     * by weight. A phase of every 2nd move still strikes once between its telegraphs.
     */
    private void bossTurn(GameState s, BattleState battle, CreepFighter boss, Random rng) {
        BossTemplate.Phase phase = boss.bossPhase();
        if (battle.round() % (phase.telegraphEvery() + 1) == 0) {
            boss.announce(phase.telegraph());
            battle.say(phase.telegraph().telegraphLine().replace("{name}", boss.name()));
        } else {
            strike(s, battle, boss, battle.player(), EnemyAI.weighted(phase.moves(), rng), false);
        }
    }

    /** One hit with its lines: the move, how well it landed, the damage, a status put on or washed off. */
    private void strike(GameState s, BattleState battle, Fighter attacker, Fighter defender, Skill move,
                        boolean telegraphed) {
        int announced = battle.nextLine();
        battle.say(move == Skill.STRIKE
                ? subject(attacker) + (attacker.isPlayer() ? " strike!" : " attacks!")
                : subject(attacker) + (attacker.isPlayer() ? " use " : " uses ") + move.name() + "!");
        int phaseBefore = phaseOf(defender);
        DamageCalculator.Hit hit = calculator.hit(attacker, defender, move, telegraphed, s.rng());
        switch (hit.affinity()) {
            case W -> battle.say("It's super effective!");
            case R -> battle.say("It's not very effective...");
            case I -> battle.say("It has no effect.");
            case N -> { }
        }
        if (hit.crit()) {
            battle.say("A critical hit!");
        }
        int lost = defender.takeDamage(hit.amount());
        battle.record(new HitLine(defender.isPlayer(), lost, hit.crit(), hit.affinity(), hit.status(), move.element(), announced));
        if (hit.amount() > 0) {
            battle.say(subject(defender) + (defender.isPlayer() ? " take " : " takes ") + lost + " damage.");
            for (Status removed : defender.statuses().removeBy(move.element(), content.statuses())) {
                battle.say(possessive(defender) + " " + content.status(removed).name().toLowerCase() + " fades.");
            }
        }
        if (telegraphed && defender.isPlayer() && defender.guarding() && attacker instanceof CreepFighter creep
                && creep.boss() != null) {
            BossTemplate boss = creep.boss();
            creep.statuses().apply(content.status(boss.exposeStatus()));     // "Guard the fall" (P-67)
            battle.say(boss.exposeLine());
        }
        if (hit.status() != null && defender.isAlive()) {
            StatusRule rule = content.status(hit.status());
            defender.statuses().apply(rule);
            battle.say(subject(defender) + (defender.isPlayer() ? " are " : " is ") + adjective(hit.status()) + "!");
            if (rule.burstPct() > 0) {
                int burst = defender.takeDamage(Math.max(1, (int) Math.round(defender.maxHp() * rule.burstPct() / 100.0)));
                battle.record(new HitLine(defender.isPlayer(), burst, false, null, hit.status(), null, battle.nextLine()));
                battle.say(subject(defender) + (defender.isPlayer() ? " lose " : " loses ") + burst + " more.");
            }
        }
        checkPhase(battle, defender, phaseBefore);
    }

    // =====================================================================
    // the end
    // =====================================================================

    private BattleOutcome outcomeOf(GameState s, BattleState battle) {
        if (!battle.enemy().isAlive()) {
            return won(s, battle);
        }
        if (!battle.player().isAlive()) {
            return lost(s, battle);
        }
        return BattleOutcome.ONGOING;
    }

    /**
     * Victory (P-63): Shards for the enemy's level, its drops rolled, and a
     * share of FP restored (Lanterns are the full refill). One
     * {@code nextInt(100)} per drop, in table order.
     */
    private BattleOutcome won(GameState s, BattleState battle) {
        CreepFighter enemy = battle.enemy();
        if (enemy.boss() != null) {
            return bossWon(s, battle, enemy.boss());
        }
        EventBus bus = s.bus();
        int shards = content.rules().creepShards(enemy.level());
        List<String> drops = new ArrayList<>();
        for (CreepTemplate.Drop drop : enemy.template().drops()) {
            if (s.rng().nextInt(100) < drop.pct()) {
                drops.add(drop.itemId());
            }
        }
        battle.say("The " + enemy.name() + " is defeated!");
        battle.say("You gain " + shards + " Shards.");
        bus.publish(GameEvent.of(EventType.BATTLE_WON, "Victory over the " + enemy.name(),
                "creepId", enemy.template().id()));
        Rewards.grantShards(s, bus, shards);
        for (String itemId : drops) {
            Item item = items.get(itemId);
            Rewards.grantItem(s, bus, item, 1);
            battle.say("The " + enemy.name() + " dropped a " + item.name() + ".");
        }
        s.stats().restoreFp(s.stats().maxFp() * content.rules().victoryFpPct() / 100);
        battle.end(BattleOutcome.WON, new BattleState.Rewards(shards, drops));
        return BattleOutcome.WON;
    }

    /**
     * A boss falls (P-67): boss Shards and its reward item, or, if the item is already owned or the bag is
     * full, Shards of one creep of the fallback level (never empty, P-65). FP as after any victory.
     */
    private BattleOutcome bossWon(GameState s, BattleState battle, BossTemplate boss) {
        EventBus bus = s.bus();
        BattleRules rules = content.rules();
        int shards = rules.bossShards(boss.level());
        List<String> taken = new ArrayList<>();
        Item reward = items.get(boss.rewardItem());
        boolean owned = !reward.isStackable() && s.inventory().has(reward.id());
        if (!owned && takes(s, reward)) {
            taken.add(reward.id());
            bus.publish(new GameEvent(EventType.ITEM_GAINED, Map.of("itemId", reward.id())));
        } else {
            shards += rules.creepShards(boss.fallbackLevel());
        }
        battle.say("The " + boss.name() + " is defeated!");
        battle.say("You gain " + shards + " Shards.");
        if (!taken.isEmpty()) {
            battle.say("You take the " + reward.name() + ".");
        }
        bus.publish(GameEvent.of(EventType.BATTLE_WON, "Victory over the " + boss.name(), "creepId", boss.id()));
        Rewards.grantShards(s, bus, shards);
        s.stats().restoreFp(s.stats().maxFp() * rules.victoryFpPct() / 100);
        battle.end(BattleOutcome.WON, new BattleState.Rewards(shards, taken));
        return BattleOutcome.WON;
    }

    /** Puts one of the item in the bag; false if there is no room. */
    private static boolean takes(GameState s, Item item) {
        try {
            return s.inventory().add(item, 1);
        } catch (InventoryFullException full) {
            return false;
        }
    }

    private BattleOutcome lost(GameState s, BattleState battle) {
        battle.say("You collapse...");
        s.bus().publish(new GameEvent(EventType.BATTLE_LOST, Map.of("creepId", battle.enemy().template().id())));
        battle.end(BattleOutcome.LOST, null);
        return BattleOutcome.LOST;
    }

    // =====================================================================
    // words
    // =====================================================================

    private static String subject(Fighter f) {
        return f.isPlayer() ? "You" : "The " + f.name();
    }

    private static String possessive(Fighter f) {
        return f.isPlayer() ? "Your" : "The " + f.name() + "'s";
    }

    private static String adjective(Status status) {
        return switch (status) {
            case BLEED -> "bleeding";
            case POISON -> "poisoned";
            case BURN -> "burning";
            case FROST -> "frozen";
            case STUN -> "stunned";
            case EXPOSED -> "exposed";
        };
    }
}
