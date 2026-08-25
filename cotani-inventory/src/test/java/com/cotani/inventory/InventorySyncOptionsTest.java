package com.cotani.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.inventory.api.InventorySyncOptions;
import org.junit.jupiter.api.Test;

class InventorySyncOptionsTest {

    @Test
    void shouldCreateAllPresetOptions() {
        var all = InventorySyncOptions.all();
        assertTrue(all.syncMainContents());
        assertTrue(all.syncArmor());
        assertTrue(all.syncOffHand());
        assertTrue(all.syncEnderChest());
        assertTrue(all.syncExperience());
        assertTrue(all.syncHealthAndFood());
        assertTrue(all.syncPotionEffects());
        assertTrue(all.syncGameMode());
        assertTrue(all.syncFlight());
    }

    @Test
    void shouldCreateInventoryOnlyPresetOptions() {
        var invOnly = InventorySyncOptions.inventoryOnly();
        assertTrue(invOnly.syncMainContents());
        assertTrue(invOnly.syncArmor());
        assertTrue(invOnly.syncOffHand());
        assertFalse(invOnly.syncEnderChest());
        assertFalse(invOnly.syncExperience());
        assertFalse(invOnly.syncHealthAndFood());
        assertFalse(invOnly.syncPotionEffects());
        assertFalse(invOnly.syncGameMode());
        assertFalse(invOnly.syncFlight());
    }

    @Test
    void shouldCreateCustomOptionsViaBuilder() {
        var custom = InventorySyncOptions.builder()
                .syncMainContents(true)
                .syncArmor(false)
                .syncOffHand(false)
                .syncEnderChest(false)
                .syncExperience(true)
                .syncHealthAndFood(false)
                .syncPotionEffects(false)
                .syncGameMode(false)
                .syncFlight(false)
                .build();

        assertTrue(custom.syncMainContents());
        assertFalse(custom.syncArmor());
        assertTrue(custom.syncExperience());
    }
}
