package com.cotani.reward.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.reward.api.ItemGrant;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class RewardItemResolverTest {
    private final RewardItemResolver resolver = RewardItemResolver.vanilla();

    @Test
    @EnabledIf("isBukkitRegistryAvailable")
    void resolvesCaseInsensitiveVanillaMaterialKeys() {
        var namespaced = resolver.resolve(new ItemGrant("MINECRAFT:DiAmOnD", 3));

        assertEquals(Material.DIAMOND, namespaced.getType());
        assertEquals(3, namespaced.getAmount());
    }

    @Test
    void rejectsUnknownMaterialKeys() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(new ItemGrant("not-a-material", 1)));
    }

    @Test
    @EnabledIf("isBukkitRegistryAvailable")
    void rejectsAirMaterialKeys() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(new ItemGrant("air", 1)));
    }

    static boolean isBukkitRegistryAvailable() {
        try {
            Material.DIAMOND.isAir();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }
}
