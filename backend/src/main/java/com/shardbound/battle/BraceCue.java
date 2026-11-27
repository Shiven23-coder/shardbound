package com.shardbound.battle;

/**
 * What a battle shows while a telegraphed blow is pending (P-67), to teach Guard: the warning ({@code {move}} is
 * the move's name) and the Guard option's subtitle. Creeps share one from {@code balance.json}; a boss has its own.
 */
public record BraceCue(String warning, String guardDetail) {

    /** The warning for this move. */
    public String warningFor(String move) {
        return warning.replace("{move}", move);
    }
}
