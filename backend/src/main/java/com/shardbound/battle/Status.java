package com.shardbound.battle;

/** Statuses a move can inflict (V2_SPEC §4.5); their numbers are in content/statuses.json. */
public enum Status {
    BLEED, POISON, BURN, FROST, STUN,
    /** Put on a boss whose telegraphed blow was guarded (P-67): its defence drops. */
    EXPOSED
}
