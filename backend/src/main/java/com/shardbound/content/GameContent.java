package com.shardbound.content;

import com.shardbound.achievement.Achievement;
import com.shardbound.battle.BattleContent;
import com.shardbound.battle.Encounter;
import com.shardbound.cutscene.Cutscene;
import com.shardbound.dialogue.DialogueGraph;
import com.shardbound.engine.Ending;
import com.shardbound.engine.Objective;
import com.shardbound.item.Item;
import com.shardbound.model.Npc;
import com.shardbound.quest.QuestManager;
import com.shardbound.progression.ProgressionRules;
import com.shardbound.world.World;
import com.shardbound.world.WorldRules;

import java.util.List;
import java.util.Map;

/**
 * All game content as immutable Java objects, built once at startup and
 * shared by every session (D20). Nothing in here changes during play.
 *
 * @param endings every ending sorted by priority (resolver endings and END_QUICK)
 */
public record GameContent(
        Map<String, Npc> npcs,
        Map<String, Item> items,
        Map<String, Encounter> encounters,
        Map<String, Achievement> achievements,
        List<Ending> endings,
        List<Objective> objectives,
        QuestManager questManager,
        DialogueGraph graph,
        World world,
        WorldRules worldRules,
        BattleContent battle,
        ProgressionRules progression,
        Map<String, Cutscene> cutscenes) {

    /** Must run once at startup: computes the quest log order with Kahn's algorithm (spec §6.5). */
    public void prepareForPlay() {
        questManager.computeOrder();
    }

    /** The ending whose node is {@code nodeId}, or null. O(e). */
    public Ending endingAtNode(String nodeId) {
        for (Ending ending : endings) {
            if (ending.nodeId().equals(nodeId)) {
                return ending;
            }
        }
        return null;
    }
}
