package com.shardbound.rules;

import com.shardbound.engine.GameState;

/** Compares how many of an item the player owns. */
public record ItemCondition(String itemId, String itemName, Op op, int value) implements Condition {

    @Override
    public boolean test(GameState state) {
        return op.compare(state.inventory().count(itemId), value);
    }

    @Override
    public String requirement() {
        if (op == Op.GE && value == 1) {
            return itemName;
        }
        if ((op == Op.EQ || op == Op.LE) && value == 0) {
            return "not carrying " + itemName;
        }
        return itemName + " × " + op.pretty() + " " + value;
    }
}
