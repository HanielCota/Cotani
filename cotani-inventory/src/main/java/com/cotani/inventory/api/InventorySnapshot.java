package com.cotani.inventory.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Immutable snapshot capturing all items, equipment, enderchest, active effects,
 * and attributes of a player at a specific point in time.
 */
@NullMarked
public record InventorySnapshot(
        UUID playerId,
        int version,
        long createdAt,
        List<ItemStack> mainContents,
        List<ItemStack> armorContents,
        ItemStack offHand,
        List<ItemStack> enderChestContents,
        int totalExperience,
        int level,
        float exp,
        double health,
        double maxHealth,
        int foodLevel,
        float saturation,
        List<PotionEffectSnapshot> potionEffects,
        GameMode gameMode,
        boolean allowFlight,
        boolean flying) {

    public InventorySnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mainContents, "mainContents");
        Objects.requireNonNull(armorContents, "armorContents");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(enderChestContents, "enderChestContents");
        Objects.requireNonNull(potionEffects, "potionEffects");
        Objects.requireNonNull(gameMode, "gameMode");

        mainContents = copyItemList(mainContents);
        armorContents = copyItemList(armorContents);
        offHand = cloneItem(offHand);
        enderChestContents = copyItemList(enderChestContents);
        potionEffects = List.copyOf(potionEffects);
    }

    @Override
    public List<ItemStack> mainContents() {
        return copyItemList(mainContents);
    }

    @Override
    public List<ItemStack> armorContents() {
        return copyItemList(armorContents);
    }

    @Override
    public ItemStack offHand() {
        return cloneItem(offHand);
    }

    @Override
    public List<ItemStack> enderChestContents() {
        return copyItemList(enderChestContents);
    }

    private static List<ItemStack> copyItemList(List<ItemStack> source) {
        var copy = new ArrayList<ItemStack>(source.size());
        for (var item : source) {
            copy.add(cloneItem(item));
        }
        return Collections.unmodifiableList(copy);
    }

    private static ItemStack cloneItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ItemStack.empty();
        }
        return item.clone();
    }

    /**
     * Creates a new builder for constructing {@link InventorySnapshot}.
     *
     * @param playerId player UUID
     * @return snapshot builder
     */
    public static Builder builder(UUID playerId) {
        return new Builder(playerId);
    }

    /**
     * Creates an empty baseline snapshot for a player.
     *
     * @param playerId player UUID
     * @return empty snapshot
     */
    public static InventorySnapshot empty(UUID playerId) {
        return builder(playerId).build();
    }

    /**
     * Builder for {@link InventorySnapshot}.
     */
    public static final class Builder {
        private final UUID playerId;
        private int version = 1;
        private long createdAt = System.currentTimeMillis();
        private List<ItemStack> mainContents = new ArrayList<>();
        private List<ItemStack> armorContents = new ArrayList<>();
        private ItemStack offHand = ItemStack.empty();
        private List<ItemStack> enderChestContents = new ArrayList<>();
        private int totalExperience = 0;
        private int level = 0;
        private float exp = 0.0f;
        private double health = 20.0;
        private double maxHealth = 20.0;
        private int foodLevel = 20;
        private float saturation = 5.0f;
        private List<PotionEffectSnapshot> potionEffects = new ArrayList<>();
        private GameMode gameMode = GameMode.SURVIVAL;
        private boolean allowFlight = false;
        private boolean flying = false;

        private Builder(UUID playerId) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder mainContents(List<ItemStack> mainContents) {
            this.mainContents = Objects.requireNonNull(mainContents, "mainContents");
            return this;
        }

        public Builder armorContents(List<ItemStack> armorContents) {
            this.armorContents = Objects.requireNonNull(armorContents, "armorContents");
            return this;
        }

        public Builder offHand(ItemStack offHand) {
            this.offHand = Objects.requireNonNull(offHand, "offHand");
            return this;
        }

        public Builder enderChestContents(List<ItemStack> enderChestContents) {
            this.enderChestContents = Objects.requireNonNull(enderChestContents, "enderChestContents");
            return this;
        }

        public Builder experience(int totalExperience, int level, float exp) {
            this.totalExperience = Math.max(0, totalExperience);
            this.level = Math.max(0, level);
            this.exp = Math.clamp(exp, 0.0f, 1.0f);
            return this;
        }

        public Builder health(double health, double maxHealth) {
            this.health = Math.max(0.0, health);
            this.maxHealth = Math.max(1.0, maxHealth);
            return this;
        }

        public Builder food(int foodLevel, float saturation) {
            this.foodLevel = Math.clamp(foodLevel, 0, 20);
            this.saturation = Math.max(0.0f, saturation);
            return this;
        }

        public Builder potionEffects(List<PotionEffectSnapshot> potionEffects) {
            this.potionEffects = Objects.requireNonNull(potionEffects, "potionEffects");
            return this;
        }

        public Builder gameMode(GameMode gameMode) {
            this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
            return this;
        }

        public Builder flight(boolean allowFlight, boolean flying) {
            this.allowFlight = allowFlight;
            this.flying = flying;
            return this;
        }

        public InventorySnapshot build() {
            return new InventorySnapshot(
                    playerId,
                    version,
                    createdAt,
                    mainContents,
                    armorContents,
                    offHand,
                    enderChestContents,
                    totalExperience,
                    level,
                    exp,
                    health,
                    maxHealth,
                    foodLevel,
                    saturation,
                    potionEffects,
                    gameMode,
                    allowFlight,
                    flying);
        }
    }
}
