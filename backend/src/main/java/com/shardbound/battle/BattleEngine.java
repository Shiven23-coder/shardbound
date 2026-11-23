package com.shardbound.battle;

import com.shardbound.battle.ai.EnemyAI;
import com.shardbound.engine.GameState;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.item.Consumable;
import com.shardbound.item.Flask;
import com.shardbound.item.Item;
import com.shardbound.rules.Rewards;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    /** Rebuilds a stored battle (P-22): the same fighters, then the stored HP, statuses and round on top. */
    public BattleState restore(GameState s, BattleSnapshot snapshot) {
        CreepTemplate template = content.creeps().get(snapshot.creepId());
        if (template == null) {
            throw new IllegalStateException("Stored battle against unknown enemy " + snapshot.creepId());
        }
        BattleState battle = build(s, template, snapshot.level(), snapshot.creepRef(), snapshot.encounterId(),
                snapshot.canFlee());
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
     * Plays one round: the player's action and the enemy's, faster first (the
     * player on ties), each starting with its statuses' tick (V2_SPEC §4.5).
     * The order is rebuilt every round from current speed, so Frost counts
     * (P-54). The caller checked the action is allowed. O(1) per round.
     */
    public BattleOutcome act(GameState s, BattleAction action) {
        BattleState battle = s.battle();
        battle.startRound();
        for (Fighter fighter : turnOrder(battle)) {
            if (!fighter.isAlive()) {
                continue;
            }
            fighter.setGuarding(false);                 // Guard lasts until the guarder's next turn
            boolean skip = startOfTurn(battle, fighter);
            BattleOutcome after = outcomeOf(s, battle);
            if (after != BattleOutcome.ONGOING) {
                return after;
            }
            if (skip) {
                battle.say(subject(fighter) + (fighter.isPlayer() ? " are" : " is") + " stunned and cannot move!");
                continue;
            }
            if (fighter.isPlayer()) {
                if (playerTurn(s, battle, action)) {
                    battle.say("You got away safely!");
                    battle.end(BattleOutcome.FLED, null);
                    return BattleOutcome.FLED;
                }
            } else {
                enemyTurn(s, battle);
            }
            after = outcomeOf(s, battle);
            if (after != BattleOutcome.ONGOING) {
                return after;
            }
        }
        return BattleOutcome.ONGOING;
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
            int lost = fighter.takeDamage(damage.getValue());
            battle.record(new HitLine(fighter.isPlayer(), lost, false, null, damage.getKey(), null, battle.nextLine()));
            battle.say(subject(fighter) + (fighter.isPlayer() ? " suffer " : " suffers ") + lost + " from "
                    + content.status(damage.getKey()).name().toLowerCase() + ".");
        }
        return tick.skipTurn();
    }

    /** Performs the player's action; returns true if the player escaped. */
    private boolean playerTurn(GameState s, BattleState battle, BattleAction action) {
        PlayerFighter player = battle.player();
        switch (action.kind()) {
            case STRIKE -> strike(s, battle, player, battle.enemy(), Skill.STRIKE, false);
            case GUARD -> {
                player.setGuarding(true);
                battle.say("You raise your guard.");
            }
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
     * The enemy's turn: a move announced last turn lands now; otherwise the
     * AI decides, and a telegraphed move is only announced (P-54).
     */
    private void enemyTurn(GameState s, BattleState battle) {
        CreepFighter enemy = battle.enemy();
        Random rng = s.rng();
        Skill announced = enemy.nextTelegraph().orElse(null);
        if (announced != null) {
            strike(s, battle, enemy, battle.player(), announced, true);
            return;
        }
        EnemyAI.Choice choice = EnemyAI.forStrategy(enemy.template().ai())
                .decide(enemy, battle.player(), content.rules(), rng);
        if (choice.guards()) {
            enemy.setGuarding(true);
            battle.say("The " + enemy.name() + " braces itself.");
        } else if (choice.move().telegraph()) {
            enemy.announce(choice.move());
            battle.say(choice.move().telegraphLine().replace("{name}", enemy.name()));
        } else {
            strike(s, battle, enemy, battle.player(), choice.move(), false);
        }
    }

    /** One hit with its lines: the move, how well it landed, the damage, a status put on or washed off. */
    private void strike(GameState s, BattleState battle, Fighter attacker, Fighter defender, Skill move,
                        boolean telegraphed) {
        int announced = battle.nextLine();
        battle.say(move == Skill.STRIKE
                ? subject(attacker) + (attacker.isPlayer() ? " strike!" : " attacks!")
                : subject(attacker) + (attacker.isPlayer() ? " use " : " uses ") + move.name() + "!");
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
        };
    }
}
