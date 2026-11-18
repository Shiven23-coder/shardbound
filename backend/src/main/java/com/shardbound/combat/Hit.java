package com.shardbound.combat;

/** One damage number for the UI. targetIndex -1 means the player was hit. */
public record Hit(int targetIndex, int amount, boolean crit) {

    public static final int PLAYER = -1;
}
