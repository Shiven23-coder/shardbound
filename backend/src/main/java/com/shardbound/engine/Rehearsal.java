package com.shardbound.engine;

import com.shardbound.model.Faction;
import com.shardbound.progression.Attribute;
import com.shardbound.quest.QuestStatus;

import java.util.List;
import java.util.Map;

/**
 * A story point a test can put a run at in one step (the {@code e2e} profile only, P-67): the flags, quests,
 * standing and gear the route there would leave, the raises bought on the way, and the Lantern rested at last,
 * where the player stands. Everything else is as a new game has it.
 *
 * @param raises attribute raises bought in this order, one level each
 */
public record Rehearsal(Map<String, Integer> flags, Map<String, QuestStatus> quests, Map<Faction, Integer> reputation,
                        Map<String, Integer> affinity, List<String> items, String weapon, List<String> skills,
                        List<Attribute> raises, String lantern, String area, String spawn) {

    public Rehearsal {
        flags = Map.copyOf(flags);
        quests = Map.copyOf(quests);
        reputation = Map.copyOf(reputation);
        affinity = Map.copyOf(affinity);
        items = List.copyOf(items);
        skills = List.copyOf(skills);
        raises = List.copyOf(raises);
    }
}
