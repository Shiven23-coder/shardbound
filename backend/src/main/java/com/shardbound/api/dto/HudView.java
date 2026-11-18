package com.shardbound.api.dto;

/** Top-bar numbers. weapon is null when nothing is equipped. */
public record HudView(int hp, int maxHp, int energy, int maxEnergy, int gold, int level, int xp, int xpToNext,
                      int atk, int def, WeaponInfo weapon) {

    public record WeaponInfo(String id, String name, String tier, int attack) { }
}
