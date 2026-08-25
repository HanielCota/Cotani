package com.cotani.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.inventory.api.CrossServerTransferLock;
import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.inventory.api.InventorySyncOptions;
import com.cotani.inventory.api.InventorySyncService;
import com.cotani.inventory.api.TransferLease;
import com.cotani.inventory.internal.service.DefaultInventorySyncService;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.MockedStatic;

@EnabledIf("isPaperRuntimeAvailable")
class InventorySyncServiceTest {

    static boolean isPaperRuntimeAvailable() {
        try {
            org.bukkit.inventory.ItemStack.empty();
            return true;
        } catch (Throwable _) {
            return false;
        }
    }

    private PaperTaskScheduler scheduler;
    private InventoryRepository repository;
    private CrossServerTransferLock transferLock;
    private InventorySyncService service;
    private Player player;
    private PlayerInventory inventory;
    private Inventory enderChest;
    private UUID playerId;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        scheduler = mock(PaperTaskScheduler.class);
        repository = mock(InventoryRepository.class);
        transferLock = mock(CrossServerTransferLock.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        enderChest = mock(Inventory.class);
        playerId = UUID.randomUUID();

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getEnderChest()).thenReturn(enderChest);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());
        bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

        doAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(2);
                    try {
                        return CompletableFuture.completedFuture(supplier.get());
                    } catch (Throwable error) {
                        return CompletableFuture.failedFuture(error);
                    }
                })
                .when(scheduler)
                .supply(any(ExecutionTarget.class), anyString(), org.mockito.ArgumentMatchers.<Supplier<Object>>any());

        service = new DefaultInventorySyncService(scheduler, repository, transferLock);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void shouldCapturePlayerInventorySnapshot() {
        var sword = new ItemStack(Material.IRON_SWORD);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[] {sword});
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(inventory.getItemInOffHand()).thenReturn(ItemStack.empty());
        when(enderChest.getContents()).thenReturn(new ItemStack[0]);
        when(player.getTotalExperience()).thenReturn(100);
        when(player.getLevel()).thenReturn(5);
        when(player.getExp()).thenReturn(0.5f);
        when(player.getHealth()).thenReturn(20.0);
        when(player.getFoodLevel()).thenReturn(20);
        when(player.getSaturation()).thenReturn(5.0f);

        var snapshot = service.captureAsync(player).toCompletableFuture().join();

        assertNotNull(snapshot);
        assertEquals(playerId, snapshot.playerId());
        assertEquals(1, snapshot.mainContents().size());
        assertEquals(Material.IRON_SWORD, snapshot.mainContents().getFirst().getType());
        assertEquals(100, snapshot.totalExperience());
        assertEquals(5, snapshot.level());
    }

    @Test
    void shouldApplySnapshotToPlayer() {
        var snapshot = InventorySnapshot.builder(playerId)
                .mainContents(List.of(new ItemStack(Material.BOW)))
                .experience(500, 20, 0.2f)
                .health(15.0, 20.0)
                .food(18, 3.0f)
                .gameMode(GameMode.ADVENTURE)
                .flight(true, true)
                .build();

        service.applyAsync(player, snapshot, InventorySyncOptions.all())
                .toCompletableFuture()
                .join();

        verify(player).setTotalExperience(500);
        verify(player).setLevel(20);
        verify(player).setExp(0.2f);
        verify(player).setHealth(15.0);
        verify(player).setFoodLevel(18);
        verify(player).setGameMode(GameMode.ADVENTURE);
        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
    }

    @Test
    void shouldSaveAndLoadSnapshot() {
        var snapshot = InventorySnapshot.empty(playerId);
        when(repository.saveSnapshotAsync(any(InventorySnapshot.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(repository.findLatestAsync(playerId)).thenReturn(CompletableFuture.completedFuture(Optional.of(snapshot)));

        when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(inventory.getItemInOffHand()).thenReturn(ItemStack.empty());
        when(enderChest.getContents()).thenReturn(new ItemStack[0]);

        var saved = service.saveAsync(player).toCompletableFuture().join();
        assertNotNull(saved);
        verify(repository).saveSnapshotAsync(any(InventorySnapshot.class));

        var loaded = service.loadLatestAsync(playerId).toCompletableFuture().join();
        assertTrue(loaded.isPresent());
        assertEquals(playerId, loaded.get().playerId());
    }

    @Test
    void shouldRollbackToHistoricalSnapshot() {
        long timestamp = 1700000000000L;
        var snapshot = InventorySnapshot.builder(playerId).createdAt(timestamp).build();

        when(repository.findByIdAsync(playerId, timestamp))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(snapshot)));

        boolean restored =
                service.rollbackAsync(player, timestamp).toCompletableFuture().join();
        assertTrue(restored);
    }

    @Test
    void shouldCoordinateCrossServerTransferLock() {
        var lease = new TransferLease(playerId, "lease-token");
        when(transferLock.tryLockAsync(playerId, Duration.ofSeconds(10)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(lease)));
        when(transferLock.unlockAsync(lease)).thenReturn(CompletableFuture.completedFuture(null));

        var acquiredLease = service.beginTransferAsync(playerId, Duration.ofSeconds(10))
                .toCompletableFuture()
                .join();
        assertTrue(acquiredLease.isPresent());
        assertEquals(lease, acquiredLease.orElseThrow());
        verify(transferLock).tryLockAsync(playerId, Duration.ofSeconds(10));

        service.completeTransferAsync(lease).toCompletableFuture().join();
        verify(transferLock).unlockAsync(lease);
    }
}
