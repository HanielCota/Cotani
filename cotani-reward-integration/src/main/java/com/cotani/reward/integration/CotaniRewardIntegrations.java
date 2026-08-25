package com.cotani.reward.integration;

import com.cotani.economy.EconomyService;
import com.cotani.inventory.api.InventorySyncService;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

/** Factories for the standard reward settlement adapters. */
@NullMarked
public final class CotaniRewardIntegrations {
    private CotaniRewardIntegrations() {}

    public static RewardEconomyGrantHandler economy(EconomyService economyService) {
        return new RewardEconomyGrantHandler(economyService);
    }

    public static RewardInventoryGrantHandler vanillaInventory(Plugin plugin, InventorySyncService inventoryService) {
        return inventory(plugin, inventoryService, RewardItemResolver.vanilla());
    }

    public static RewardInventoryGrantHandler inventory(
            Plugin plugin, InventorySyncService inventoryService, RewardItemResolver itemResolver) {
        return new RewardInventoryGrantHandler(
                Objects.requireNonNull(plugin, "plugin"),
                Objects.requireNonNull(inventoryService, "inventoryService"),
                Objects.requireNonNull(itemResolver, "itemResolver"));
    }
}
