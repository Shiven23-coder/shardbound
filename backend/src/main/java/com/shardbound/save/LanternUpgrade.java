package com.shardbound.save;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Format 3 → 4 (V5, P-61 … P-63): the healing draughts become the refillable
 * Lantern Draught, full; no Lantern has been rested at and no Shards have been
 * lost yet.
 *
 * <p>The values are frozen V5 knowledge: they never change with later content.
 */
final class LanternUpgrade implements SaveUpgrade {

    private static final String OLD_DRAUGHT = "healing_draught";
    private static final String FLASK = "lantern_draught";
    private static final int FLASK_CHARGES = 3;

    @Override
    public int fromVersion() {
        return 3;
    }

    @Override
    public ObjectNode apply(ObjectNode document) {
        ArrayNode inventory = document.withArray("inventory");
        for (int i = inventory.size() - 1; i >= 0; i--) {
            JsonNode entry = inventory.get(i);
            if (OLD_DRAUGHT.equals(entry.path("itemId").asString()) || FLASK.equals(entry.path("itemId").asString())) {
                inventory.remove(i);
            }
        }
        inventory.addObject().put("itemId", FLASK).put("qty", 1);
        document.putObject("flaskCharges").put(FLASK, FLASK_CHARGES);
        document.putNull("lastLantern");
        document.putNull("lostShards");
        return document;
    }
}
