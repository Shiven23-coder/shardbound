package com.shardbound.engine;

import com.shardbound.view.ChoiceView;
import com.shardbound.view.CombatView;
import com.shardbound.view.HudView;
import com.shardbound.view.SessionView;
import com.shardbound.view.SessionView.AffinityView;
import com.shardbound.view.SessionView.DialogueView;
import com.shardbound.view.SessionView.EndingView;
import com.shardbound.view.SessionView.InventoryView;
import com.shardbound.view.SessionView.ItemView;
import com.shardbound.view.SessionView.JournalView;
import com.shardbound.view.SessionView.QuestView;
import com.shardbound.view.SessionView.ReputationView;
import com.shardbound.view.ToastView;
import com.shardbound.view.WorldView;
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
import com.shardbound.model.Npc;
import com.shardbound.quest.Quest;
import com.shardbound.quest.QuestStatus;
import com.shardbound.dialogue.Choice;
import com.shardbound.dialogue.DialogueNode;
import com.shardbound.world.AreaMap;
import com.shardbound.world.MapObject;
import com.shardbound.world.ObjectType;
import com.shardbound.world.World;
import com.shardbound.world.WorldPosition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Turns a GameState into the SessionView JSON shape (V2_SPEC §8). Every value
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

    /** Full view for the current state. Time O(n) over the state's collections and the area's objects. */
    public SessionView build(GameState s) {
        DialogueNode node = s.currentNode() == null ? null : content.graph().node(s.currentNode());
        return new SessionView(
                s.sessionId(), s.mode(), s.demoMode(), world(s),
                s.mode() == Mode.DIALOGUE ? dialogue(s, node) : null,
                hud(s), inventory(s), quests(s), reputation(s), affinity(s), journal(s),
                drainToasts(s),
                s.mode() == Mode.COMBAT ? combat(s) : null,
                s.mode() == Mode.ENDED ? ending(s, node) : null,
                objective(s),
                s.offerReload());
    }

    /** The current objective (P-46): the first whose conditions hold; none once the game has ended. */
    private @Nullable String objective(GameState s) {
        if (s.mode() == Mode.ENDED) {
            return null;
        }
        return content.objectives().stream().filter(o -> o.conditions().test(s)).map(Objective::text).findFirst().orElse(null);
    }

    /** The area, its light state, the player, the NPCs present and the objects absent right now. */
    private WorldView world(GameState s) {
        WorldPosition p = s.position();
        World world = content.world();
        World.Area area = world.area(p.areaId());
        AreaMap map = world.mapOf(p.areaId());
        List<WorldView.NpcView> npcs = new ArrayList<>();
        List<String> hidden = new ArrayList<>();
        for (MapObject o : map.objects()) {
            if (!WorldObjects.active(s, p.areaId(), o)) {
                hidden.add(o.name());
            } else if (o.type() == ObjectType.NPC) {
                npcs.add(new WorldView.NpcView(o.name(), o.property("npcId"), map.centre(o.x()), map.centre(o.y())));
            }
        }
        return new WorldView(area.id(), area.name(), area.region(), map.key(), lightState(s, world.regions().get(area.region())),
                new WorldView.PlayerView(p.x(), p.y(), p.facing()), npcs, hidden);
    }

    /** The first light state whose flag is set; the last one is the default. */
    private static String lightState(GameState s, World.Region region) {
        for (World.LightState state : region.lightStates()) {
            if (state.flag() == null || s.flag(state.flag()) != 0) {
                return state.id();
            }
        }
        return region.lightStates().getLast().id();
    }

    private DialogueView dialogue(GameState s, DialogueNode node) {
        List<ChoiceView> choices = choices(s, node);
        return new DialogueView(node.id(), node.title(), node.speaker(), node.body(), choices, isMessage(node, choices));
    }

    /** One unlocked option that ends the dialogue and changes nothing: a message to close, not a choice. */
    private boolean isMessage(DialogueNode node, List<ChoiceView> shown) {
        if (shown.size() != 1 || shown.getFirst().locked()) {
            return false;
        }
        Choice only = content.graph().choicesFrom(node.id()).stream()
                .filter(c -> c.id() == shown.getFirst().id()).findFirst().orElseThrow();
        return only.endsDialogue() && only.effects().isEmpty();
    }

    /** Locked choices keep their reason, unless hidden_when_locked. O(k · c). */
    private List<ChoiceView> choices(GameState s, DialogueNode node) {
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
        boolean outsideCombat = s.mode() == Mode.WORLD || s.mode() == Mode.DIALOGUE;
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
            boolean usable = outsideCombat && item instanceof Consumable;
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
            DialogueNode node = content.graph().node(step.nodeId());
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

    private EndingView ending(GameState s, DialogueNode node) {
        Ending ending = content.endingAtNode(node.id());
        String id = ending == null ? node.id() : ending.id();
        String title = ending == null ? node.title() : ending.title();
        return new EndingView(id, title, node.body(), s.endingSeconds(), s.endingsFound(), content.endings().size());
    }
}
