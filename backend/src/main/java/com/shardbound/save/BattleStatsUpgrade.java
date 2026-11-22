package com.shardbound.save;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Format 2 → 3 (V4, P-55): v1's level, XP and energy give way to attributes,
 * Shards and FP. XP becomes Shards one for one; HP and FP are filled (the new
 * maximums are far above v1's, and the stats clamp them); the opening skills
 * go into the loadout; the creeps are rolled anew when the game is loaded.
 *
 * <p>The values are frozen V4 knowledge: they never change with later content.
 */
final class BattleStatsUpgrade implements SaveUpgrade {

    /** Larger than any maximum; the stats clamp it to full HP and FP. */
    private static final int FULL = 9999;
    private static final String[] OPENING_SKILLS = {"rending_cut", "crystal_pulse"};

    @Override
    public int fromVersion() {
        return 2;
    }

    @Override
    public ObjectNode apply(ObjectNode document) {
        int xp = document.path("xp").asInt(0);
        document.remove("xp");
        document.put("level", 1);
        document.remove("energy");
        document.put("hp", FULL);
        document.put("fp", FULL);
        document.put("shards", xp);
        document.putObject("attributes");
        ArrayNode skills = document.putArray("skills");
        ArrayNode loadout = document.putArray("loadout");
        for (String skill : OPENING_SKILLS) {
            skills.add(skill);
            loadout.add(skill);
        }
        while (loadout.size() < 4) {
            loadout.addNull();
        }
        document.putNull("creeps");
        return document;
    }
}
