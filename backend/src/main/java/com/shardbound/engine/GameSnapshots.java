package com.shardbound.engine;

import com.shardbound.combat.CombatEngine;
import com.shardbound.combat.CombatState;
import com.shardbound.content.GameContent;
import com.shardbound.exception.SaveIncompatibleException;
import com.shardbound.item.Inventory;
import com.shardbound.item.Item;
import com.shardbound.model.Faction;
import com.shardbound.model.Stats;
import com.shardbound.quest.QuestStatus;
import com.shardbound.save.RunSnapshot;
import com.shardbound.save.SaveSnapshot;
import com.shardbound.save.SaveSnapshot.ItemEntry;
import com.shardbound.save.SaveSnapshot.StepEntry;
import com.shardbound.story.StoryNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between a live {@link GameState} and its stored snapshots.
 *
 * <p>Restoring is strict: a snapshot that names a node, item, quest or faction
 * the current content does not have is rejected with
 * {@link SaveIncompatibleException}. Content renames must ship with a save
 * upgrade instead of silently dropping the player's things.
 */
final class GameSnapshots {

    private final GameContent content;
    private final CombatEngine combatEngine;

    GameSnapshots(GameContent content, CombatEngine combatEngine) {
        this.content = content;
        this.combatEngine = combatEngine;
    }

    // ---------------------------------------------------------------------
    // live state → snapshot
    // ---------------------------------------------------------------------

    SaveSnapshot save(GameState s) {
        Stats stats = s.stats();
        List<ItemEntry> inventory = new ArrayList<>();
        for (Map.Entry<String, Integer> stack : s.inventory().quantities().entrySet()) {
            inventory.add(new ItemEntry(stack.getKey(), stack.getValue()));
        }
        Map<String, Integer> reputation = new HashMap<>();
        for (Map.Entry<Faction, Integer> entry : s.reputationView().entrySet()) {
            reputation.put(entry.getKey().name(), entry.getValue());
        }
        Map<String, String> quests = new HashMap<>();
        for (Map.Entry<String, QuestStatus> entry : s.questsView().entrySet()) {
            quests.put(entry.getKey(), entry.getValue().name());
        }
        List<StepEntry> history = new ArrayList<>();
        for (Step step : s.historyOldestFirst()) {
            history.add(new StepEntry(step.nodeId(), step.choiceId()));
        }
        return new SaveSnapshot(s.currentNode(), stats.hp(), stats.energy(), stats.level(), stats.xp(), stats.gold(),
                inventory, s.inventory().equippedWeaponId(), s.flagsView(), reputation, s.affinityView(), quests,
                history, s.rngSeed(), s.demoMode(), s.playSeconds());
    }

    RunSnapshot run(GameState s) {
        CombatState combat = s.combat();
        return new RunSnapshot(save(s), s.mode().name(), combat == null ? null : combat.snapshot(),
                s.offerReload(), s.endingSeconds(), s.endingsFound());
    }

    /** The location shown for a save slot (the summary column {@code area_id}). */
    String areaOf(GameState s) {
        StoryNode node = content.graph().node(s.currentNode());
        return node == null ? s.currentNode() : node.locationId();
    }

    // ---------------------------------------------------------------------
    // snapshot → live state (no bus yet: the engine wires it)
    // ---------------------------------------------------------------------

    GameState restoreSave(SaveSnapshot d, String sessionId, int playerId) {
        if (content.graph().node(d.currentNode()) == null) {
            throw incompatible("story node", d.currentNode());
        }
        Stats stats = new Stats(d.hp(), d.energy(), d.level(), d.xp(), d.gold());   // the rest is derived (D6, D28)
        Inventory inventory = new Inventory();
        for (ItemEntry entry : d.inventory()) {
            Item item = content.items().get(entry.itemId());
            if (item == null) {
                throw incompatible("item", entry.itemId());
            }
            inventory.add(item, entry.qty());
        }
        if (d.equipped() != null && inventory.equip(d.equipped()) == null) {
            throw incompatible("equipped weapon", d.equipped());
        }
        GameState s = new GameState(sessionId, playerId, d.currentNode(), stats, inventory, d.rngSeed(),
                d.demoMode(), d.playSeconds());
        content.questManager().initialStates(s);                // quests added to content since the save
        for (Map.Entry<String, String> quest : d.quests().entrySet()) {
            if (content.questManager().quest(quest.getKey()) == null) {
                throw incompatible("quest", quest.getKey());
            }
            s.setQuestStatus(quest.getKey(), QuestStatus.valueOf(quest.getValue()));
        }
        for (Map.Entry<String, Integer> flag : d.flags().entrySet()) {
            s.setFlag(flag.getKey(), flag.getValue());
        }
        for (Map.Entry<String, Integer> rep : d.reputation().entrySet()) {
            s.putReputation(faction(rep.getKey()), rep.getValue());
        }
        for (Map.Entry<String, Integer> aff : d.affinity().entrySet()) {
            s.putAffinity(aff.getKey(), aff.getValue());
        }
        for (StepEntry step : d.history()) {                     // oldest first → newest ends on top
            s.historyStack().push(new Step(step.nodeId(), step.choiceId()));
        }
        return s;
    }

    GameState restoreRun(RunSnapshot run, String sessionId, int playerId) {
        GameState s = restoreSave(run.game(), sessionId, playerId);
        s.setMode(Mode.valueOf(run.mode()));
        if (run.combat() != null) {
            try {
                s.setCombat(combatEngine.restore(s, run.combat()));
            } catch (IllegalStateException e) {
                throw new SaveIncompatibleException("The fight in progress no longer matches the game content: "
                        + e.getMessage());
            }
        }
        s.setOfferReload(run.offerReload());
        s.recordEnding(run.endingSeconds(), run.endingsFound());
        return s;
    }

    private static Faction faction(String name) {
        try {
            return Faction.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw incompatible("faction", name);
        }
    }

    private static SaveIncompatibleException incompatible(String what, String id) {
        return new SaveIncompatibleException("This save refers to a " + what + " ('" + id
                + "') that is not in the game any more.");
    }
}
