<div align="center">

<img src="../logo.png" alt="Cotani logo" width="220">

# cotani-inventory

</div>

Loss-less binary inventory synchronization, historical snapshots, rollbacks, and dupe-proof cross-server transfers.

## Overview

`cotani-inventory` provides asynchronous inventory capture, persistence, and synchronization for Paper and Folia
servers. Its standard serializer uses Paper's native binary `ItemStack` representation, preserving modern data
components and the supported player-state fields without converting them through lossy legacy formats.

## Features

- **Native Binary Serialization**: Encodes inventories using Paper's native `ItemStack.serializeAsBytes()` to preserve 1.20.5+ / 1.21+ Data Components without loss.
- **Folia & Paper Thread Safety**: Captures and applies player state exclusively on the player's entity thread via `PaperTaskScheduler.entity(...)`.
- **Granular Sync Options**: Selectively sync main inventory, armor, offhand, enderchest, experience, health, food, potion effects, gamemode, or flight flags.
- **Historical Snapshots & Rollback**: Persists snapshots with timestamps for audit trails and one-command player inventory rollbacks.
- **Cross-Server Dupe Protection**: Integrates with `CrossServerTransferLock` (or `cotani-redis`) to prevent players from joining another server before inventory saves complete.

## Usage

### 1. Initialization

```java
import com.cotani.inventory.CotaniInventories;
import com.cotani.inventory.InventoryModule;

InventoryModule inventoryModule = CotaniInventories.create(plugin, scheduler, storage);
InventorySyncService syncService = inventoryModule.service();
```

### 2. Saving and Loading Snapshots

```java
UUID playerId = player.getUniqueId();
syncService.saveAsync(player)
    .thenAccept(snapshot -> scheduler.entity(playerId, () -> {
        Player current = Bukkit.getPlayer(playerId);
        if (current != null) {
            current.sendMessage("Inventory saved successfully!");
        }
    }));

// Load and apply latest snapshot on join
syncService.loadAndApplyAsync(player)
    .thenAccept(maybeSnapshot -> {
        maybeSnapshot.ifPresent(snapshot -> scheduler.entity(playerId, () -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current != null) {
                current.sendMessage("Inventory restored!");
            }
        }));
    });
```

### 3. Rolling Back to a Previous Snapshot

```java
UUID playerId = player.getUniqueId();
syncService.rollbackAsync(player, snapshotTimestamp)
    .thenAccept(restored -> {
        if (restored) {
            scheduler.entity(playerId, () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current != null) {
                    current.sendMessage("Inventory rolled back to historical snapshot.");
                }
            });
        }
    });
```

### 4. Cross-Server Transfer with Dupe Lock

```java
UUID playerId = player.getUniqueId();

// Acquire lock before server transition
syncService.beginTransferAsync(playerId, Duration.ofSeconds(15))
    .thenCompose(maybeLease -> {
        if (maybeLease.isEmpty()) {
            scheduler.entity(playerId, () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current != null) {
                    current.sendMessage("Transfer already in progress!");
                }
            });
            return CompletableFuture.completedFuture(null);
        }
        var lease = maybeLease.orElseThrow();
        var saveStage = new CompletableFuture<Void>();
        scheduler.entity(playerId, () -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current == null) {
                saveStage.completeExceptionally(new IllegalStateException("Player is no longer online"));
                return;
            }
            syncService.saveAsync(current).whenComplete((_, error) -> {
                if (error != null) {
                    saveStage.completeExceptionally(error);
                } else {
                    saveStage.complete(null);
                }
            });
        });
        return saveStage
            .thenAccept(_ -> scheduler.entity(playerId, () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current != null) {
                    sendPlayerToTargetServer(current, "survival-2");
                }
            }))
            .thenCompose(_ -> syncService.completeTransferAsync(lease));
    });
```

## Hard Rules & Best Practices

1. **Entity Thread Safety**: Always allow `InventorySyncService` to transition to the player's entity thread before touching `PlayerInventory` or player attributes.
2. **Immutable Snapshots**: `InventorySnapshot` instances are strictly immutable; modifying items returned by accessors will not mutate the snapshot.
3. **No Blocking Calls**: Never invoke `future.join()` or `future.get()` when saving or loading snapshots.
