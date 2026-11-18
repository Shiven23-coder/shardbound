package com.shardbound.model;

/**
 * The four factions of Valdren. Fixed keys, so reputation is stored in an
 * {@code EnumMap<Faction, Integer>} (array-backed, O(1)). The ids must match
 * the {@code faction} table; ContentAssembler checks this at startup.
 */
public enum Faction {
    ELDMERE("Eldmere"),
    SYLVAN("Sylvan Wardens"),
    CROWN("The Crown"),
    ASHEN("Ashen Circle");

    private final String displayName;

    Faction(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
