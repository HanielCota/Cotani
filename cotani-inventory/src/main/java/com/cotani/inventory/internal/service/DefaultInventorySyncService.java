package com.cotani.inventory.internal.service;

import com.cotani.api.InternalApi;
import com.cotani.inventory.api.CrossServerTransferLock;
import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.inventory.api.InventorySyncOptions;
import com.cotani.inventory.api.InventorySyncService;
import com.cotani.inventory.api.PotionEffectSnapshot;
import com.cotani.inventory.api.TransferLease;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Default implementation of {@link InventorySyncService}.
 */
@InternalApi
@NullMarked
public final class DefaultInventorySyncService implements InventorySyncService {

    private final PaperTaskScheduler scheduler;
    private final InventoryRepository repository;
    private final CrossServerTransferLock transferLock;

    public DefaultInventorySyncService(
            PaperTaskScheduler scheduler, InventoryRepository repository, CrossServerTransferLock transferLock) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transferLock = Objects.requireNonNull(transferLock, "transferLock");
    }

    @Override
    public CompletionStage<InventorySnapshot> captureAsync(Player player) {
        Objects.requireNonNull(player, "player");
        var playerId = player.getUniqueId();
        return scheduler.supply(
                ExecutionTarget.entity(playerId), "inventory-capture", () -> captureOnEntityThread(playerId));
    }

    @Override
    public CompletionStage<Void> applyAsync(Player player, InventorySnapshot snapshot, InventorySyncOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(options, "options");
        var playerId = player.getUniqueId();
        return scheduler.supply(ExecutionTarget.entity(playerId), "inventory-apply", () -> {
            applyOnEntityThread(playerId, snapshot, options);
            return null;
        });
    }

    @Override
    public CompletionStage<Void> mutateAsync(
            UUID playerId, UnaryOperator<InventorySnapshot> mutation, InventorySyncOptions options) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(options, "options");
        return scheduler.supply(ExecutionTarget.entity(playerId), "inventory-mutate", () -> {
            var current = captureOnEntityThread(playerId);
            var next = Objects.requireNonNull(mutation.apply(current), "mutation result");
            if (!playerId.equals(next.playerId())) {
                throw new IllegalArgumentException("mutation result must belong to playerId");
            }
            applyOnEntityThread(playerId, next, options);
            return null;
        });
    }

    @Override
    public CompletionStage<InventorySnapshot> saveAsync(Player player) {
        Objects.requireNonNull(player, "player");
        return captureAsync(player)
                .thenCompose(snapshot -> repository.saveSnapshotAsync(snapshot).thenApply(_ -> snapshot));
    }

    @Override
    public CompletionStage<Optional<InventorySnapshot>> loadLatestAsync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.findLatestAsync(playerId);
    }

    @Override
    public CompletionStage<Optional<InventorySnapshot>> loadAndApplyAsync(Player player, InventorySyncOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(options, "options");

        return loadLatestAsync(player.getUniqueId()).thenCompose(optionalSnapshot -> {
            if (optionalSnapshot.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            return applyAsync(player, optionalSnapshot.get(), options).thenApply(_ -> optionalSnapshot);
        });
    }

    @Override
    public CompletionStage<List<InventorySnapshot>> historyAsync(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return repository.findHistoryAsync(playerId, limit);
    }

    @Override
    public CompletionStage<Boolean> rollbackAsync(Player player, long snapshotTimestamp, InventorySyncOptions options) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(options, "options");

        return repository.findByIdAsync(player.getUniqueId(), snapshotTimestamp).thenCompose(optionalSnapshot -> {
            if (optionalSnapshot.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return applyAsync(player, optionalSnapshot.get(), options).thenApply(_ -> true);
        });
    }

    @Override
    public CompletionStage<Optional<TransferLease>> beginTransferAsync(UUID playerId, Duration lockDuration) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (lockDuration.isZero() || lockDuration.isNegative()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
        return transferLock.tryLockAsync(playerId, lockDuration);
    }

    @Override
    public CompletionStage<Void> completeTransferAsync(TransferLease lease) {
        Objects.requireNonNull(lease, "lease");
        return transferLock.unlockAsync(lease);
    }

    private static InventorySnapshot captureOnEntityThread(UUID playerId) {
        var player = requireOnlinePlayer(playerId);
        var inv = player.getInventory();
        var mainContents = copyItems(inv.getStorageContents());
        var armorContents = copyItems(inv.getArmorContents());
        var offHand = cloneItem(inv.getItemInOffHand());
        var enderChest = copyItems(player.getEnderChest().getContents());

        int totalExp = player.getTotalExperience();
        int level = player.getLevel();
        float exp = player.getExp();

        double health = player.getHealth();
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;

        var effects = new ArrayList<PotionEffectSnapshot>();
        for (var effect : player.getActivePotionEffects()) {
            effects.add(PotionEffectSnapshot.fromBukkit(effect));
        }

        return InventorySnapshot.builder(playerId)
                .createdAt(System.currentTimeMillis())
                .mainContents(mainContents)
                .armorContents(armorContents)
                .offHand(offHand)
                .enderChestContents(enderChest)
                .experience(totalExp, level, exp)
                .health(health, maxHealth)
                .food(player.getFoodLevel(), player.getSaturation())
                .potionEffects(effects)
                .gameMode(player.getGameMode())
                .flight(player.getAllowFlight(), player.isFlying())
                .build();
    }

    private static void applyOnEntityThread(UUID playerId, InventorySnapshot snapshot, InventorySyncOptions options) {
        var player = requireOnlinePlayer(playerId);
        var inv = player.getInventory();

        if (options.syncMainContents()) {
            inv.setStorageContents(snapshot.mainContents().toArray(ItemStack[]::new));
        }
        if (options.syncArmor()) {
            inv.setArmorContents(snapshot.armorContents().toArray(ItemStack[]::new));
        }
        if (options.syncOffHand()) {
            inv.setItemInOffHand(snapshot.offHand());
        }
        if (options.syncEnderChest()) {
            player.getEnderChest().setContents(snapshot.enderChestContents().toArray(ItemStack[]::new));
        }
        if (options.syncExperience()) {
            player.setTotalExperience(snapshot.totalExperience());
            player.setLevel(snapshot.level());
            player.setExp(snapshot.exp());
        }
        if (options.syncHealthAndFood()) {
            var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(snapshot.maxHealth());
            }
            player.setHealth(Math.min(snapshot.health(), snapshot.maxHealth()));
            player.setFoodLevel(snapshot.foodLevel());
            player.setSaturation(snapshot.saturation());
        }
        if (options.syncPotionEffects()) {
            for (var existing : player.getActivePotionEffects()) {
                player.removePotionEffect(existing.getType());
            }
            for (var effect : snapshot.potionEffects()) {
                player.addPotionEffect(effect.toBukkit());
            }
        }
        if (options.syncGameMode()) {
            player.setGameMode(snapshot.gameMode());
        }
        if (options.syncFlight()) {
            player.setAllowFlight(snapshot.allowFlight());
            player.setFlying(snapshot.flying());
        }
    }

    private static Player requireOnlinePlayer(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            throw new IllegalStateException("Player is offline: " + playerId);
        }
        return player;
    }

    private static List<ItemStack> copyItems(ItemStack @org.jspecify.annotations.Nullable [] items) {
        if (items == null) {
            return List.of();
        }
        var list = new ArrayList<ItemStack>(items.length);
        for (var item : items) {
            list.add(cloneItem(item));
        }
        return list;
    }

    private static ItemStack cloneItem(@org.jspecify.annotations.Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return ItemStack.empty();
        }
        return item.clone();
    }
}
