package com.cotani.reward.integration;

import com.cotani.reward.api.ItemGrant;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/** Resolves an item grant into a Paper item stack on the player's entity thread. */
@FunctionalInterface
@NullMarked
public interface RewardItemResolver {
    ItemStack resolve(ItemGrant grant);

    /** Resolves vanilla material keys such as {@code diamond} or {@code minecraft:diamond}. */
    static RewardItemResolver vanilla() {
        return grant -> {
            var key = grant.itemKey().toLowerCase(Locale.ROOT);
            var separator = key.indexOf(':');
            if (separator >= 0) {
                key = key.substring(separator + 1);
            }
            var material = Material.matchMaterial(key);
            if (material == null || material.isAir()) {
                throw new IllegalArgumentException("Unknown vanilla reward item: " + grant.itemKey());
            }
            return ItemStack.of(material, grant.amount());
        };
    }

    static RewardItemResolver require(RewardItemResolver resolver) {
        return Objects.requireNonNull(resolver, "resolver");
    }
}
