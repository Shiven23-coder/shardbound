package com.shardbound.engine;

import com.shardbound.battle.BattleEngine;
import com.shardbound.battle.BattleSnapshot;
import com.shardbound.battle.BattleState;
import com.shardbound.battle.Encounter;
import com.shardbound.dialogue.DialogueNode;
import com.shardbound.progression.Attribute;
import com.shardbound.progression.Attributes;
import com.shardbound.progression.ProgressionRules;
import com.shardbound.save.SaveSnapshot.CreepsEntry;
import org.jspecify.annotations.Nullable;
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
import com.shardbound.save.SaveSnapshot.WorldEntry;
import com.shardbound.world.AreaMap;
import com.shardbound.world.Facing;
import com.shardbound.world.MapObject;
import com.shardbound.world.WorldPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Converts between a live {@link GameState} and its stored snapshots.
 *
 * <p>Restoring is strict: a snapshot that names a node, area, item, quest or faction
 * the current content does not have is rejected with
 * {@link SaveIncompatibleException}. Content renames must ship with a save
 * upgrade instead of silently dropping the player's things.
 */
final class GameSnapshots {

    private final GameContent content;
    private final BattleEngine battleEngine;

    GameSnapshots(GameContent content, BattleEngine battleEngine) {
        this.content = content;
        this.battleEngine = battleEngine;
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
        WorldPosition p = s.position();
        WorldEntry world = new WorldEntry(p.areaId(), null, p.x(), p.y(), p.facing().name());
        Map<String, Integer> attributes = new HashMap<>();
        stats.attributes().asMap().forEach((attribute, value) -> attributes.put(attribute.name(), value));
        CreepRoster creeps = s.creeps();
        CreepsEntry roster = new CreepsEntry(creeps.levelsView(), List.copyOf(creeps.defeatedView()), creeps.fledView());
        return new SaveSnapshot(s.currentNode(), world, stats.hp(), stats.fp(), stats.gold(), stats.shards(), stats.level(), attributes,
                s.knownSkills(), s.loadout(), roster, inventory, s.inventory().equippedWeaponId(), s.flagsView(),
                reputation, s.affinityView(), quests, history, s.rngSeed(), s.demoMode(), s.playSeconds());
    }

    RunSnapshot run(GameState s) {
        BattleState battle = s.battle();
        List<RunSnapshot.ActionEntry> actions = s.recentActions().entries().stream()
                .map(e -> new RunSnapshot.ActionEntry(e.key(), e.requestHash(), e.runVersion())).toList();
        return new RunSnapshot(save(s), s.mode().name(), battle == null ? null : BattleSnapshot.of(battle),
                s.offerReload(), s.endingSeconds(), s.endingsFound(), actions);
    }

    /** The area shown for a save slot (the summary column {@code area_id}). */
    String areaOf(GameState s) {
        return s.position().areaId();
    }

    // ---------------------------------------------------------------------
    // snapshot → live state (no bus yet: the engine wires it)
    // ---------------------------------------------------------------------

    GameState restoreSave(SaveSnapshot d, String sessionId, int playerId) {
        if (d.currentNode() != null && content.graph().node(d.currentNode()) == null) {
            throw incompatible("dialogue node", d.currentNode());
        }
        WorldPosition position = position(d.world());
        Map<Attribute, Integer> attributes = new EnumMap<>(Attribute.class);
        for (Map.Entry<String, Integer> entry : d.attributes().entrySet()) {
            try {
                attributes.put(Attribute.valueOf(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException e) {
                throw incompatible("attribute", entry.getKey());
            }
        }
        ProgressionRules rules = content.progression();
        Stats stats = new Stats(Attributes.of(attributes, rules.attributeStart()), rules, d.hp(), d.fp(), d.gold(),
                d.shards());                                    // maximums are derived from the attributes
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
        GameState s = new GameState(sessionId, playerId, d.currentNode(), position, stats, inventory, d.rngSeed(),
                d.demoMode(), d.playSeconds());
        for (String skill : d.skills()) {
            if (!content.battle().skills().containsKey(skill)) {
                throw incompatible("skill", skill);
            }
        }
        for (String slot : d.loadout()) {
            if (slot != null && !d.skills().contains(slot)) {
                throw incompatible("skill in the loadout", slot);
            }
        }
        s.restoreSkills(d.skills(), d.loadout());
        restoreCreeps(s, d.creeps());
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
        if (s.mode() == Mode.BATTLE) {
            s.setBattle(run.battle() != null ? restoreBattle(s, run.battle()) : restartDialogueBattle(s));
        }
        s.recentActions().restore(run.recentActions().stream()
                .map(e -> new RecentActions.Entry(e.key(), e.requestHash(), e.runVersion())).toList());
        s.setOfferReload(run.offerReload());
        s.recordEnding(run.endingSeconds(), run.endingsFound());
        return s;
    }

    private BattleState restoreBattle(GameState s, BattleSnapshot snapshot) {
        try {
            return battleEngine.restore(s, snapshot);
        } catch (IllegalStateException e) {
            throw new SaveIncompatibleException("The battle in progress no longer matches the game content: "
                    + e.getMessage());
        }
    }

    /** A v1 fight stored before format 3: the dialogue's fight starts again on the new battle screen. */
    private BattleState restartDialogueBattle(GameState s) {
        DialogueNode node = s.currentNode() == null ? null : content.graph().node(s.currentNode());
        Encounter encounter = node == null || node.encounterId() == null ? null
                : content.encounters().get(node.encounterId());
        if (encounter == null) {
            throw new SaveIncompatibleException("The stored fight cannot be found in the game content.");
        }
        return battleEngine.start(s, content.battle().creeps().get(encounter.creepId()), encounter.level(), null,
                encounter.id(), encounter.canFlee());
    }

    /** The stored roster; an upgraded save has none, so the creeps are rolled anew. */
    private void restoreCreeps(GameState s, SaveSnapshot.@Nullable CreepsEntry stored) {
        if (stored == null) {
            s.creeps().respawn(content.world(), s.rng());
        } else {
            s.creeps().restore(stored.levels(), new HashSet<>(stored.defeated()), stored.fled());
        }
    }

    /** The stored position; a spawn point (saves upgraded from format 1) wins over pixels. */
    private WorldPosition position(WorldEntry entry) {
        AreaMap map = content.world().maps().get(entry.area());
        if (map == null) {
            throw incompatible("area", entry.area());
        }
        if (entry.spawn() != null) {
            MapObject spawn = map.spawn(entry.spawn()).orElseThrow(() -> incompatible("spawn point", entry.spawn()));
            return WorldPosition.at(entry.area(), map, spawn);
        }
        try {
            return new WorldPosition(entry.area(), entry.x(), entry.y(), Facing.valueOf(entry.facing()));
        } catch (IllegalArgumentException e) {
            throw incompatible("facing", entry.facing());
        }
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
