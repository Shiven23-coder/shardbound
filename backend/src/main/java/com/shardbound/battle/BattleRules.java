package com.shardbound.battle;

/**
 * The battle numbers from balance.json {@code battle} (V2_SPEC §4.2, §4.7, P-54).
 *
 * @param hpPerLevel        creep HP growth per level above 1 (share of the base)
 * @param atkPerLevel       creep attack growth per level above 1
 * @param defPerLevel       creep defence growth per level above 1
 * @param shardsBase        Shards for a level-1 creep
 * @param shardsExponent    Shards grow as level^exponent
 * @param guardTelegraphed  damage multiplier while guarding against a telegraphed move
 * @param fleeIgnoreTiles   a creep fled from ignores the player until they are this far beyond its aggro radius
 * @param dialogueLossHpPct HP left after losing a dialogue fight (P-55, kept by P-62)
 * @param victoryFpPct      FP restored by a victory, % of max FP (P-63)
 * @param bossShardsMultiplier a boss pays this many times a creep of its level's Shards (V2_SPEC §4.3, P-67)
 */
public record BattleRules(double hpPerLevel, double atkPerLevel, double defPerLevel, double shardsBase,
                          double shardsExponent, double rollMin, double rollMax, double critMultiplier,
                          int enemyCritPct, double guardNormal, double guardTelegraphed, int statusChanceCap,
                          int fleeBasePct, int fleePctPerSpeed, int fleeMinPct, int fleeMaxPct, int fleeIgnoreTiles,
                          int defensiveGuardBelowPct, int dialogueLossHpPct, int victoryFpPct,
                          int bossShardsMultiplier) {

    /** Shards for defeating a creep of this level: round(base · level^exponent). */
    public int creepShards(int level) {
        return (int) Math.round(shardsBase * Math.pow(level, shardsExponent));
    }

    /** Shards for defeating a boss of this level (V2_SPEC §4.3). */
    public int bossShards(int level) {
        return bossShardsMultiplier * creepShards(level);
    }

    /** A creep's stat at {@code level}: round(base · (1 + growth · (level − 1))). */
    public static int scaled(int base, double growth, int level) {
        return (int) Math.round(base * (1 + growth * (level - 1)));
    }
}
