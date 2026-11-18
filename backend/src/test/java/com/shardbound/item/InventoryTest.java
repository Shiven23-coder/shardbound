package com.shardbound.item;

import com.shardbound.exception.InventoryFullException;
import com.shardbound.model.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inventory rules from spec §6.4. */
class InventoryTest {

    private static Weapon weapon(String id, Tier tier, int attack) {
        return new Weapon(id, id, "", 0, tier, attack, 5, null);
    }

    private static final Consumable POTION = new Consumable("potion", "Potion", "", 10, 15, 0);

    @Test
    void consumablesStackInOneSlot() {
        Inventory inv = new Inventory();
        inv.add(POTION, 2);
        inv.add(POTION, 3);
        assertEquals(5, inv.count("potion"));
        assertEquals(1, inv.usedSlots());
    }

    @Test
    void weaponsDoNotStackAndAreOwnedOnce() {
        Inventory inv = new Inventory();
        assertTrue(inv.add(weapon("sword", Tier.C, 8), 1));
        assertFalse(inv.add(weapon("sword", Tier.C, 8), 1));
        assertEquals(1, inv.count("sword"));
    }

    @Test
    void capacityIsTwentySlots() {
        Inventory inv = new Inventory();
        for (int i = 0; i < Inventory.CAPACITY; i++) {
            inv.add(weapon("w" + i, Tier.F, 1), 1);
        }
        assertThrows(InventoryFullException.class, () -> inv.add(weapon("one-too-many", Tier.F, 1), 1));
        inv.add(weapon("w0", Tier.F, 1), 1);           // already owned: no new slot needed, no throw
        assertEquals(20, inv.usedSlots());
    }

    @Test
    void stackingDoesNotNeedANewSlotWhenFull() {
        Inventory inv = new Inventory();
        inv.add(POTION, 1);
        for (int i = 0; i < Inventory.CAPACITY - 1; i++) {
            inv.add(weapon("w" + i, Tier.F, 1), 1);
        }
        assertTrue(inv.add(POTION, 4));
        assertEquals(5, inv.count("potion"));
    }

    @Test
    void onlyOneWeaponIsEquipped() {
        Inventory inv = new Inventory();
        inv.add(weapon("a", Tier.F, 3), 1);
        inv.add(weapon("b", Tier.E, 5), 1);
        inv.equip("a");
        inv.equip("b");
        assertEquals("b", inv.equippedWeapon().id());
        assertNull(inv.equip("potion"), "cannot equip something not owned / not a weapon");
    }

    @Test
    void removingTheEquippedWeaponUnequipsIt() {
        Inventory inv = new Inventory();
        inv.add(weapon("a", Tier.F, 3), 1);
        inv.equip("a");
        assertEquals(1, inv.remove("a", 1));
        assertNull(inv.equippedWeapon());
        assertEquals(0, inv.remove("a", 1));
    }

    @Test
    void weaponsByTierThenAttack() {
        Inventory inv = new Inventory();
        inv.add(weapon("c8", Tier.C, 8), 1);
        inv.add(weapon("sss", Tier.SSS, 26), 1);
        inv.add(weapon("c9", Tier.C, 9), 1);
        inv.add(weapon("f3", Tier.F, 3), 1);
        inv.add(POTION, 1);
        List<String> ids = inv.weaponsByTier().stream().map(Item::id).toList();
        assertEquals(List.of("sss", "c9", "c8", "f3"), ids);
        assertEquals(4, inv.weaponCount());
    }

    @Test
    void copyIsIndependent() {
        Inventory inv = new Inventory();
        inv.add(POTION, 2);
        Inventory copy = inv.copy();
        copy.remove("potion", 2);
        assertEquals(2, inv.count("potion"));
        assertEquals(0, copy.count("potion"));
    }
}
