package com.shardbound.dialogue;

/**
 * What entering a dialogue node does: TEXT shows it with its choices, COMBAT
 * starts its encounter, RESOLVER moves on to the first ending whose
 * conditions hold, ENDING finishes the game.
 */
public enum NodeType {
    TEXT, COMBAT, RESOLVER, ENDING
}
