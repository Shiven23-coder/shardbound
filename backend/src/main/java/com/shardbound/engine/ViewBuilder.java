package com.shardbound.engine;

import com.shardbound.view.ChoiceView;
import com.shardbound.view.CombatView;
import com.shardbound.view.GameView;
import com.shardbound.view.GameView.AffinityView;
import com.shardbound.view.GameView.EndingView;
import com.shardbound.view.GameView.InventoryView;
import com.shardbound.view.GameView.ItemView;
import com.shardbound.view.GameView.JournalView;
import com.shardbound.view.GameView.NodeInfo;
import com.shardbound.view.GameView.QuestView;
import com.shardbound.view.GameView.ReputationView;
import com.shardbound.view.HudView;
import com.shardbound.view.ToastView;
import com.shardbound.combat.CombatEngine;
import com.shardbound.combat.CombatState;
import com.shardbound.combat.Enemy;
import com.shardbound.combat.Hit;
import com.shardbound.content.GameContent;
import com.shardbound.item.Consumable;
import com.shardbound.item.Inventory;
import com.shardbound.item.Item;
import com.shardbound.item.ItemStack;
import com.shardbound.item.Weapon;
import com.shardbound.model.Faction;
import com.shardbound.model.Location;
import com.shardbound.model.Npc;
import com.shardbound.quest.Quest;
import com.shardbound.quest.QuestStatus;
import com.shardbound.story.Choice;
import com.shardbound.story.StoryNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Turns a GameState into the GameView JSON shape (spec §9.2). Every value
 * React shows is decided here, in Java: which choices are locked and why,
 * item order, quest order, usable items. Draining the toast queue makes
 * each toast appear exactly once.
 */
public class ViewBuilder {

    public static final int JOURNAL_SIZE = 20;

    private final GameContent content;

    public ViewBuilder(GameContent content) {
        this.content = content;
    }

    /** Full view for the current state. Time O(n) over the state's collections. */
    public GameView build(GameState s) {
        StoryNode node = content.graph().node(s.currentNode());
        Location location = content.locationOf(node.locationId());
        boolean story = s.mode() == Mode.STORY;
        return new GameView(
                s.sessionId(), s.mode(), location.region(), location.id(), location.name(), node.chapter(),
                s.demoMode(),
                new NodeInfo(node.id(), node.title(), node.body()),
                story ? choices(s, node) : List.of(),
                hud(s), inventory(s), quests(s), reputation(s), affinity(s), journal(s),
                drainToasts(s),
                s.mode() == Mode.COMBAT ? combat(s) : null,
                s.mode() == Mode.ENDED ? ending(s, node) : null,
                s.offerReload());
    }

    /** Locked choices keep their reason, unless hidden_when_locked. O(k · c). */
    private List<ChoiceView> choices(GameState s, StoryNode node) {
        List<ChoiceView> result = new ArrayList<>();
        for (Choice choice : content.graph().choicesFrom(node.id())) {
            boolean locked = !choice.conditions().test(s);
            if (locked && choice.hiddenWhenLocked()) {
                continue;
            }
            result.add(new ChoiceView(choice.id(), choice.label(), locked,
                    locked ? choice.conditions().describe() : null));
        }
        return result;
    }

    private HudView hud(GameState s) {
        var st = s.stats();
        Weapon weapon = s.inventory().equippedWeapon();
        HudView.WeaponInfo weaponInfo = weapon == null ? null
                : new HudView.WeaponInfo(weapon.id(), weapon.name(), weapon.tier().name(), weapon.attack());
        return new HudView(st.hp(), st.maxHp(), st.energy(), st.maxEnergy(), st.gold(), st.level(), st.xp(),
                st.xpToNext(), st.atk(), st.def(), weaponInfo);
    }

    /** Weapons best-first (Inventory.weaponsByTier), then everything else in pickup order. O(n log n). */
    private InventoryView inventory(GameState s) {
        Inventory inv = s.inventory();
        boolean story = s.mode() == Mode.STORY;
        List<ItemView> items = new ArrayList<>();
        for (Weapon weapon : inv.weaponsByTier()) {
            items.add(new ItemView(weapon.id(), weapon.name(), "WEAPON", weapon.description(), 1,
                    weapon.id().equals(inv.equippedWeaponId()), weapon.tier().name(), weapon.attack(),
                    weapon.critPct(), null, null, false));
        }
        for (ItemStack<Item> stack : inv.stacksView()) {
            Item item = stack.item();
            if (item instanceof Weapon) {
                continue;
            }
            Integer heal = item instanceof Consumable c ? c.heal() : null;
            Integer energy = item instanceof Consumable c ? c.energy() : null;
            boolean usable = story && item instanceof Consumable;
            items.add(new ItemView(item.id(), item.name(), item.type().name(), item.description(), stack.qty(),
                    false, null, null, null, heal, energy, usable));
        }
        return new InventoryView(inv.usedSlots(), Inventory.CAPACITY, items);
    }

    /** Non-LOCKED quests in topological order (Kahn's algorithm, spec §6.5). O(q). */
    private List<QuestView> quests(GameState s) {
        List<QuestView> result = new ArrayList<>();
        for (String questId : content.questManager().logOrder()) {
            QuestStatus status = s.questStatus(questId);
            if (status == QuestStatus.LOCKED) {
                continue;
            }
            Quest quest = content.questManager().quest(questId);
            result.add(new QuestView(quest.id(), quest.title(), quest.description(), status.name(), quest.isMain()));
        }
        return result;
    }

    private List<ReputationView> reputation(GameState s) {
        List<ReputationView> result = new ArrayList<>();
        for (Faction faction : Faction.values()) {
            result.add(new ReputationView(faction.name(), faction.displayName(), s.reputation(faction)));
        }
        return result;
    }

    /** Only NPCs the player has affinity with (has met), in content order. O(npcs). */
    private List<AffinityView> affinity(GameState s) {
        List<AffinityView> result = new ArrayList<>();
        for (Npc npc : content.npcs().values()) {
            if (s.affinityView().containsKey(npc.id())) {
                result.add(new AffinityView(npc.id(), npc.name(), s.affinity(npc.id())));
            }
        }
        return result;
    }

    private List<JournalView> journal(GameState s) {
        List<JournalView> result = new ArrayList<>();
        for (Step step : s.recentHistory(JOURNAL_SIZE)) {
            StoryNode node = content.graph().node(step.nodeId());
            Choice choice = content.graph().choiceById(step.choiceId());
            result.add(new JournalView(node == null ? step.nodeId() : node.title(),
                    choice == null ? "—" : choice.label()));
        }
        return result;
    }

    private List<ToastView> drainToasts(GameState s) {
        List<ToastView> result = new ArrayList<>();
        Queue<Toast> queue = s.toastQueue();
        while (!queue.isEmpty()) {
            Toast toast = queue.poll();
            result.add(new ToastView(toast.kind(), toast.text()));
        }
        return result;
    }

    private CombatView combat(GameState s) {
        CombatState c = s.combat();
        List<CombatView.EnemyView> enemies = new ArrayList<>();
        for (Enemy e : c.enemiesView()) {
            enemies.add(new CombatView.EnemyView(e.index(), e.name(), e.hp(), e.maxHp(), e.isDefending(), e.isAlive()));
        }
        List<CombatView.UsableItem> usable = new ArrayList<>();
        for (ItemStack<Item> stack : s.inventory().stacksView()) {
            if (stack.item() instanceof Consumable) {
                usable.add(new CombatView.UsableItem(stack.item().id(), stack.item().name(), stack.qty()));
            }
        }
        List<CombatView.HitView> hits = new ArrayList<>();
        for (Hit hit : c.lastHitsView()) {
            hits.add(new CombatView.HitView(hit.targetIndex(), hit.amount(), hit.crit()));
        }
        return new CombatView(c.encounterName(), c.round(), enemies, c.logView(), c.canFlee(),
                CombatEngine.SKILL_COST, usable, hits);
    }

    private EndingView ending(GameState s, StoryNode node) {
        Ending ending = content.endingAtNode(node.id());
        String id = ending == null ? node.id() : ending.id();
        String title = ending == null ? node.title() : ending.title();
        return new EndingView(id, title, node.body(), s.endingSeconds(), s.endingsFound(), content.endings().size());
    }
}
