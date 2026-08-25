package com.cotani.inventory.api;

import org.jspecify.annotations.NullMarked;

/**
 * Configuration options controlling which sections of a player's state are included
 * during inventory snapshot capturing, saving, or application.
 */
@NullMarked
public record InventorySyncOptions(
        boolean syncMainContents,
        boolean syncArmor,
        boolean syncOffHand,
        boolean syncEnderChest,
        boolean syncExperience,
        boolean syncHealthAndFood,
        boolean syncPotionEffects,
        boolean syncGameMode,
        boolean syncFlight) {

    private static final InventorySyncOptions ALL =
            new InventorySyncOptions(true, true, true, true, true, true, true, true, true);

    private static final InventorySyncOptions INVENTORY_ONLY =
            new InventorySyncOptions(true, true, true, false, false, false, false, false, false);

    private static final InventorySyncOptions INVENTORY_AND_ENDERCHEST =
            new InventorySyncOptions(true, true, true, true, false, false, false, false, false);

    /**
     * Options to synchronize everything (all inventories, stats, potion effects, exp, flight).
     *
     * @return full sync options
     */
    public static InventorySyncOptions all() {
        return ALL;
    }

    /**
     * Options to synchronize only the player's personal items (main, armor, offhand).
     *
     * @return inventory-only sync options
     */
    public static InventorySyncOptions inventoryOnly() {
        return INVENTORY_ONLY;
    }

    /**
     * Options to synchronize personal items and enderchest.
     *
     * @return items and enderchest options
     */
    public static InventorySyncOptions inventoryAndEnderChest() {
        return INVENTORY_AND_ENDERCHEST;
    }

    /**
     * Creates a new builder for customized sync options.
     *
     * @return options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link InventorySyncOptions}.
     */
    public static final class Builder {
        private boolean syncMainContents = true;
        private boolean syncArmor = true;
        private boolean syncOffHand = true;
        private boolean syncEnderChest = true;
        private boolean syncExperience = true;
        private boolean syncHealthAndFood = true;
        private boolean syncPotionEffects = true;
        private boolean syncGameMode = true;
        private boolean syncFlight = true;

        private Builder() {}

        public Builder syncMainContents(boolean syncMainContents) {
            this.syncMainContents = syncMainContents;
            return this;
        }

        public Builder syncArmor(boolean syncArmor) {
            this.syncArmor = syncArmor;
            return this;
        }

        public Builder syncOffHand(boolean syncOffHand) {
            this.syncOffHand = syncOffHand;
            return this;
        }

        public Builder syncEnderChest(boolean syncEnderChest) {
            this.syncEnderChest = syncEnderChest;
            return this;
        }

        public Builder syncExperience(boolean syncExperience) {
            this.syncExperience = syncExperience;
            return this;
        }

        public Builder syncHealthAndFood(boolean syncHealthAndFood) {
            this.syncHealthAndFood = syncHealthAndFood;
            return this;
        }

        public Builder syncPotionEffects(boolean syncPotionEffects) {
            this.syncPotionEffects = syncPotionEffects;
            return this;
        }

        public Builder syncGameMode(boolean syncGameMode) {
            this.syncGameMode = syncGameMode;
            return this;
        }

        public Builder syncFlight(boolean syncFlight) {
            this.syncFlight = syncFlight;
            return this;
        }

        public InventorySyncOptions build() {
            return new InventorySyncOptions(
                    syncMainContents,
                    syncArmor,
                    syncOffHand,
                    syncEnderChest,
                    syncExperience,
                    syncHealthAndFood,
                    syncPotionEffects,
                    syncGameMode,
                    syncFlight);
        }
    }
}
