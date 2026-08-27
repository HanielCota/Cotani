package com.cotani.reward.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.inventory.api.InventorySyncOptions;
import com.cotani.inventory.api.InventorySyncService;
import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.ItemGrant;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardGrantHandler.RewardSettlementContext;
import com.cotani.reward.api.RewardId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class RewardInventoryGrantHandlerTest {
    private static final UUID PLAYER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CLAIM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private Plugin plugin;
    private InventorySyncService inventoryService;
    private RewardItemResolver itemResolver;
    private RewardInventoryGrantHandler handler;
    private AtomicReference<UnaryOperator<InventorySnapshot>> mutation;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RewardTest");
        when(plugin.namespace()).thenReturn("rewardtest");
        inventoryService = mock(InventorySyncService.class);
        itemResolver = mock(RewardItemResolver.class);
        mutation = new AtomicReference<>();
        when(inventoryService.mutateAsync(eq(PLAYER_ID), any(), eq(InventorySyncOptions.inventoryOnly())))
                .thenAnswer(invocation -> {
                    mutation.set(invocation.getArgument(1));
                    return CompletableFuture.completedFuture(null);
                });
        handler = new RewardInventoryGrantHandler(plugin, inventoryService, itemResolver);
    }

    @Test
    void supportsOnlyItemGrants() {
        assertTrue(handler.supports(new ItemGrant("diamond", 1)));
        assertFalse(handler.supports(new CurrencyGrant("coins", BigDecimal.ONE)));
    }

    @Test
    void returnsFailedStageForUnsupportedGrant() {
        assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> handler.settleAsync(context(0), new CurrencyGrant("coins", BigDecimal.ONE))
                        .toCompletableFuture()
                        .join());
    }

    @Test
    @EnabledIf("isBukkitRegistryAvailable")
    void splitsLargeGrantIntoMarkedStacks() {
        var grant = new ItemGrant("diamond", 130);
        when(itemResolver.resolve(grant)).thenReturn(new ItemStack(Material.DIAMOND));

        handler.settleAsync(context(2), grant).toCompletableFuture().join();
        var result = capturedMutation().apply(emptySnapshot(3));

        var contents = result.mainContents();
        assertEquals(
                List.of(64, 64, 2), contents.stream().map(ItemStack::getAmount).toList());

        var key = new NamespacedKey(plugin, "reward-operation");
        var operation = "" + CLAIM_ID + ":item:2";
        assertEquals(operation, contents.get(0).getPersistentDataContainer().get(key, PersistentDataType.STRING));
        assertEquals(operation, contents.get(1).getPersistentDataContainer().get(key, PersistentDataType.STRING));
        assertEquals(operation, contents.get(2).getPersistentDataContainer().get(key, PersistentDataType.STRING));
        verify(inventoryService).mutateAsync(eq(PLAYER_ID), any(), eq(InventorySyncOptions.inventoryOnly()));
    }

    @Test
    @EnabledIf("isBukkitRegistryAvailable")
    void doesNotDeliverAgainWhenRecoveryFindsTheOperationMarker() {
        var grant = new ItemGrant("diamond", 1);
        when(itemResolver.resolve(grant)).thenReturn(new ItemStack(Material.DIAMOND));
        var key = new NamespacedKey(plugin, "reward-operation");
        var operation = "" + CLAIM_ID + ":item:0";
        var alreadyDelivered = new ItemStack(Material.DIAMOND);
        alreadyDelivered.editPersistentDataContainer(
                container -> container.set(key, PersistentDataType.STRING, operation));
        var before = emptySnapshot(2, alreadyDelivered, ItemStack.empty());

        handler.settleAsync(context(0), grant).toCompletableFuture().join();
        var after = capturedMutation().apply(before);

        assertEquals(before.mainContents(), after.mainContents());
    }

    @Test
    @EnabledIf("isBukkitRegistryAvailable")
    void rejectsGrantWhenThereAreNotEnoughEmptySlots() {
        var grant = new ItemGrant("diamond", 65);
        when(itemResolver.resolve(grant)).thenReturn(new ItemStack(Material.DIAMOND));

        handler.settleAsync(context(0), grant).toCompletableFuture().join();

        assertThrows(IllegalStateException.class, () -> capturedMutation().apply(emptySnapshot(1)));
    }

    @Test
    @EnabledIf("isBukkitRegistryAvailable")
    void rejectsAirReturnedByCustomResolver() {
        var grant = new ItemGrant("custom:empty", 1);
        when(itemResolver.resolve(grant)).thenReturn(ItemStack.empty());

        handler.settleAsync(context(0), grant).toCompletableFuture().join();

        assertThrows(IllegalArgumentException.class, () -> capturedMutation().apply(emptySnapshot(1)));
    }

    private UnaryOperator<InventorySnapshot> capturedMutation() {
        return Objects.requireNonNull(mutation.get(), "mutation");
    }

    static boolean isBukkitRegistryAvailable() {
        try {
            Material.DIAMOND.isAir();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static InventorySnapshot emptySnapshot(int slots, ItemStack... initialContents) {
        var contents = new ItemStack[slots];
        java.util.Arrays.fill(contents, ItemStack.empty());
        System.arraycopy(initialContents, 0, contents, 0, initialContents.length);
        return InventorySnapshot.builder(PLAYER_ID)
                .mainContents(List.of(contents))
                .build();
    }

    private static RewardSettlementContext context(int grantIndex) {
        return new RewardSettlementContext(PLAYER_ID, new RewardClaimId(CLAIM_ID), new RewardId("daily"), grantIndex);
    }
}
