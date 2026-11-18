package com.shardbound.combat;

import com.shardbound.engine.GameState;
import com.shardbound.engine.Toast;
import com.shardbound.event.EventBus;
import com.shardbound.event.EventType;
import com.shardbound.event.GameEvent;
import com.shardbound.item.Consumable;
import com.shardbound.item.Item;
import com.shardbound.rules.Rewards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Turn-based combat rules (spec §6.6). Knows nothing about the story graph:
 * {@link #act} returns a {@link CombatOutcome} and GameEngine decides which
 * node comes next (D21). Shared by all sessions; holds only immutable content.
 */
public class CombatEngine {

    public static final int SKILL_COST = 1;
    public static final int FLEE_BASE_PCT = 50;
    public static final int FLEE_PCT_PER_SPEED = 5;
    public static final int FLEE_MIN_PCT = 10;
    public static final int FLEE_MAX_PCT = 90;
    public static final int LOSS_GOLD_PCT = 30;

    /**
     * Turn order (S1): faster first; on equal speed the player goes first,
     * then enemies by encounter index. Each comparison is O(1).
     */
    static final Comparator<Combatant> TURN_ORDER =
            Comparator.comparingInt(Combatant::spd).reversed()
                      .thenComparing(c -> c.isPlayer() ? 0 : 1)
                      .thenComparingInt(c -> c instanceof Enemy e ? e.index() : -1);

    private final Map<String, EnemyTemplate> templates;
    private final Map<String, Encounter> encounters;
    private final DamageCalculator calculator = new DamageCalculator();

    public CombatEngine(Map<String, EnemyTemplate> templates, Map<String, Encounter> encounters) {
        this.templates = templates;
        this.encounters = encounters;
    }

    /**
     * Starts a fight: fresh Enemy objects from the templates (D20), energy
     * refilled to max (S2). Time O(n) for n enemies, space O(n).
     */
    public CombatState start(GameState state, String encounterId) {
        Encounter encounter = encounters.get(encounterId);
        List<Enemy> enemies = new ArrayList<>();
        for (EncounterSlot slot : encounter.slots()) {
            EnemyTemplate template = templates.get(slot.enemyId());
            for (int i = 0; i < slot.qty(); i++) {
                String name = slot.qty() > 1 ? template.name() + " " + (char) ('A' + i) : template.name();
                enemies.add(Enemy.from(template, enemies.size(), name, state.demoMode()));
            }
        }
        state.stats().refillEnergy();
        PlayerCombatant player = new PlayerCombatant("You", state.stats(), state.inventory().equippedWeapon());
        CombatState combat = new CombatState(encounterId, encounter.name(), player, enemies, encounter.canFlee());
        combat.addLog(encounter.name() + ": the fight begins!");
        return combat;
    }

    /**
     * Runs one round: the player's action plus every enemy action until the
     * player's next turn (spec §6.6). Living combatants go into a
     * {@code PriorityQueue} ordered by {@link #TURN_ORDER}; each is polled in
     * turn. The caller (GameEngine) has already checked the action is allowed.
     * Time O(n log n) for n combatants, space O(n).
     */
    public CombatOutcome act(GameState state, Action action) {
        CombatState combat = state.combat();
        combat.startRound();
        PriorityQueue<Combatant> queue = new PriorityQueue<>(TURN_ORDER);
        queue.add(combat.player());
        for (Enemy enemy : combat.enemies()) {
            if (enemy.isAlive()) {
                queue.add(enemy);
            }
        }
        while (!queue.isEmpty()) {
            Combatant actor = queue.poll();
            if (!actor.isAlive()) {
                continue;                       // killed earlier this round
            }
            actor.setDefending(false);          // DEFEND lasts until the actor's next turn
            if (actor.isPlayer()) {
                if (playerTurn(state, combat, action)) {
                    return fled(state, combat);
                }
            } else {
                enemyTurn(state, combat, (Enemy) actor);
            }
            if (combat.allEnemiesDefeated()) {
                return won(state, combat);
            }
            if (!combat.player().isAlive()) {
                return lost(state, combat);
            }
        }
        return CombatOutcome.ONGOING;
    }

    // ---------------------------------------------------------------------
    // turns
    // ---------------------------------------------------------------------

    /** Performs the player's action; returns true if the player escaped. O(1). */
    private boolean playerTurn(GameState state, CombatState combat, Action action) {
        PlayerCombatant player = combat.player();
        switch (action.type()) {
            case ATTACK -> playerHit(state, combat, action.target(), 1.0, "You strike");
            case SKILL -> {
                state.stats().spendEnergy(SKILL_COST);
                playerHit(state, combat, action.target(), DamageCalculator.SKILL_MULTIPLIER, "Crystal Pulse hits");
            }
            case DEFEND -> {
                player.setDefending(true);
                combat.addLog("You raise your guard.");
            }
            case ITEM -> useItem(state, combat, action.itemId());
            case FLEE -> {
                return tryFlee(state, combat);
            }
        }
        return false;
    }

    private void playerHit(GameState state, CombatState combat, int target, double multiplier, String verb) {
        Enemy enemy = combat.targetOrFirstAlive(target);
        if (enemy == null) {
            return;
        }
        PlayerCombatant player = combat.player();
        int critPct = player.weapon() == null ? 0 : player.weapon().critPct();
        DamageRoll roll = calculator.roll(player, enemy, player.weapon(), critPct, multiplier, state.rng());
        enemy.takeDamage(roll.amount());
        combat.recordHit(new Hit(enemy.index(), roll.amount(), roll.crit()));
        combat.addLog(verb + " " + enemy.name() + " for " + roll.amount() + (roll.crit() ? " (critical!)" : "")
                + (enemy.isAlive() ? "." : ". It falls."));
    }

    private void useItem(GameState state, CombatState combat, String itemId) {
        Item item = state.inventory().get(itemId);
        if (item instanceof Consumable consumable) {
            Rewards.useConsumable(state, state.bus(), consumable);
            combat.addLog("You use " + consumable.name() + ".");
        }
    }

    /** Flee chance = 50% + (playerSpd − avgEnemySpd) × 5%, clamped 10–90%. O(n). */
    int fleeChance(CombatState combat) {
        double diff = combat.player().spd() - combat.averageEnemySpeed();
        int chance = (int) Math.round(FLEE_BASE_PCT + diff * FLEE_PCT_PER_SPEED);
        return Math.max(FLEE_MIN_PCT, Math.min(FLEE_MAX_PCT, chance));
    }

    private boolean tryFlee(GameState state, CombatState combat) {
        int chance = fleeChance(combat);
        if (state.rng().nextInt(100) < chance) {
            combat.addLog("You break away and run!");
            return true;
        }
        combat.addLog("You try to flee, but they cut you off.");
        return false;
    }

    private void enemyTurn(GameState state, CombatState combat, Enemy enemy) {
        Action decision = enemy.ai().decide(enemy, combat);
        enemy.countTurn();
        if (decision.type() == ActionType.DEFEND) {
            enemy.setDefending(true);
            combat.addLog(enemy.name() + " braces behind its guard.");
            return;
        }
        boolean heavy = decision.type() == ActionType.SKILL;
        double multiplier = heavy ? DamageCalculator.HEAVY_MULTIPLIER : 1.0;
        PlayerCombatant player = combat.player();
        DamageRoll roll = calculator.roll(enemy, player, null, DamageCalculator.ENEMY_CRIT_PCT, multiplier, state.rng());
        player.takeDamage(roll.amount());
        combat.recordHit(new Hit(Hit.PLAYER, roll.amount(), roll.crit()));
        String verb = heavy ? " unleashes a heavy blow" : " hits you";
        combat.addLog(enemy.name() + verb + " for " + roll.amount() + (roll.crit() ? " (critical!)" : "") + ".");
    }

    // ---------------------------------------------------------------------
    // outcomes (rewards and penalties are applied here; GameEngine routes)
    // ---------------------------------------------------------------------

    /** XP and gold from every defeated enemy (D8). O(n). */
    private CombatOutcome won(GameState state, CombatState combat) {
        int xp = 0;
        int gold = 0;
        for (Enemy enemy : combat.enemies()) {
            xp += enemy.xpReward();
            gold += enemy.goldReward();
        }
        EventBus bus = state.bus();
        bus.publish(GameEvent.of(EventType.COMBAT_WON, "Victory: " + combat.encounterName(),
                "encounterId", combat.encounterId()));
        Rewards.grantXp(state, bus, xp);
        Rewards.grantGold(state, bus, gold);
        combat.addLog("Victory!");
        return CombatOutcome.WON;
    }

    /** Loss penalty: lose 30% of gold, hp set to 50% of max (spec §6.6). O(1). */
    private CombatOutcome lost(GameState state, CombatState combat) {
        EventBus bus = state.bus();
        int goldLost = state.stats().gold() * LOSS_GOLD_PCT / 100;
        state.stats().setHp(state.stats().maxHp() / 2);
        bus.publish(GameEvent.of(EventType.COMBAT_LOST, "Defeated by " + combat.encounterName(),
                "encounterId", combat.encounterId()));
        if (goldLost > 0) {
            Rewards.grantGold(state, bus, -goldLost);
        }
        combat.addLog("You fall.");
        return CombatOutcome.LOST;
    }

    /** Fleeing has no penalty (D9). O(1). */
    private CombatOutcome fled(GameState state, CombatState combat) {
        state.addToast(new Toast("COMBAT", "You escaped from " + combat.encounterName()));
        return CombatOutcome.FLED;
    }
}
