package com.shardbound.combat;

import java.util.List;

/** Immutable encounter definition: its enemies and whether fleeing is allowed. */
public record Encounter(String id, String name, boolean canFlee, List<EncounterSlot> slots) {

    public Encounter {
        slots = List.copyOf(slots);
    }
}
