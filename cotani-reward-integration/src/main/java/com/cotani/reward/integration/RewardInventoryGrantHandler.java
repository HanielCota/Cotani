package com.cotani.reward.integration;

import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.inventory.api.InventorySyncOptions;
import com.cotani.inventory.api.InventorySyncService;
import com.cotani.reward.api.ItemGrant;
import com.cotani.reward.api.RewardGrant;
import com.cotani.reward.api.RewardGrantHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

/**
 * Settles item grants through the entity-thread-safe inventory mutation API.
 *
 * <p>Each delivered stack carries a claim/index marker. A recovered claim therefore becomes a
 * no-op when the previous item delivery is still present in the player's inventory.
 */
@NullMarked
public final class RewardInventoryGrantHandler implements RewardGrantHandler {
    private final InventorySyncService inventoryService;
    private final RewardItemResolver itemResolver;
    private final NamespacedKey operationKey;

    public RewardInventoryGrantHandler(
            Plugin plugin, InventorySyncService inventoryService, RewardItemResolver itemResolver) {
        Objects.requireNonNull(plugin, "plugin");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.itemResolver = RewardItemResolver.require(itemResolver);
        this.operationKey = new NamespacedKey(plugin, "reward-operation");
    }

    @Override
    public boolean supports(RewardGrant grant) {
        return grant instanceof ItemGrant;
    }

    @Override
    public CompletionStage<Void> settleAsync(RewardSettlementContext context, RewardGrant grant) {
        if (!(grant instanceof ItemGrant item)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Unsupported grant: " + grant.getClass().getName()));
        }
        var operation = context.claimId().value() + ":item:" + context.grantIndex();
        return inventoryService.mutateAsync(
                context.playerId(),
                snapshot -> addMarkedStacks(snapshot, item, operation),
                InventorySyncOptions.inventoryOnly());
    }

    private InventorySnapshot addMarkedStacks(InventorySnapshot snapshot, ItemGrant grant, String operation) {
        var contents = new ArrayList<>(snapshot.mainContents());
        if (containsOperation(
                contents, snapshot.armorContents(), snapshot.offHand(), snapshot.enderChestContents(), operation)) {
            return snapshot;
        }

        var template = Objects.requireNonNull(itemResolver.resolve(grant), "itemResolver result");
        if (template.getType().isAir()) {
            throw new IllegalArgumentException("itemResolver must return a non-air item");
        }

        var maxStackSize = Math.max(1, template.getMaxStackSize());
        var stackCount = ((grant.amount() - 1) / maxStackSize) + 1;
        var emptySlots = contents.stream()
                .filter(item -> item == null || item.getType().isAir())
                .count();
        if (emptySlots < stackCount) {
            throw new IllegalStateException("Player inventory is full for reward item " + grant.itemKey());
        }

        var remaining = grant.amount();
        for (var index = 0; index < contents.size() && remaining > 0; index++) {
            var existing = contents.get(index);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            var stack = template.clone();
            stack.setAmount(Math.min(remaining, maxStackSize));
            stack.editPersistentDataContainer(
                    container -> container.set(operationKey, PersistentDataType.STRING, operation));
            contents.set(index, stack);
            remaining -= stack.getAmount();
        }

        return InventorySnapshot.builder(snapshot.playerId())
                .version(snapshot.version())
                .createdAt(snapshot.createdAt())
                .mainContents(contents)
                .armorContents(snapshot.armorContents())
                .offHand(snapshot.offHand())
                .enderChestContents(snapshot.enderChestContents())
                .experience(snapshot.totalExperience(), snapshot.level(), snapshot.exp())
                .health(snapshot.health(), snapshot.maxHealth())
                .food(snapshot.foodLevel(), snapshot.saturation())
                .potionEffects(snapshot.potionEffects())
                .gameMode(snapshot.gameMode())
                .flight(snapshot.allowFlight(), snapshot.flying())
                .build();
    }

    private boolean containsOperation(
            List<ItemStack> mainContents,
            List<ItemStack> armorContents,
            ItemStack offHand,
            List<ItemStack> enderChestContents,
            String operation) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.concat(
                                java.util.stream.Stream.concat(mainContents.stream(), armorContents.stream()),
                                java.util.stream.Stream.of(offHand)),
                        enderChestContents.stream())
                .filter(Objects::nonNull)
                .map(item -> item.getPersistentDataContainer().get(operationKey, PersistentDataType.STRING))
                .anyMatch(operation::equals);
    }
}
