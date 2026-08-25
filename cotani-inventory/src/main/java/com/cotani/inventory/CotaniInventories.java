package com.cotani.inventory;

import com.cotani.inventory.api.CrossServerTransferLock;
import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySerializer;
import com.cotani.inventory.api.InventorySyncService;
import com.cotani.inventory.internal.module.DefaultInventoryModule;
import com.cotani.inventory.internal.repository.CreateInventoryTablesMigration;
import com.cotani.inventory.internal.repository.StorageInventoryRepository;
import com.cotani.inventory.internal.serializer.BinaryInventorySerializer;
import com.cotani.inventory.internal.service.DefaultInventorySyncService;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Main factory and entry point for the Cotani Inventory synchronization module.
 */
@NullMarked
public final class CotaniInventories {

    private CotaniInventories() {}

    /**
     * Returns the list of SQL migrations required by the inventory module.
     *
     * @return migrations list
     */
    public static List<Migration> migrations() {
        return List.of(new CreateInventoryTablesMigration());
    }

    /**
     * Returns the standard binary serializer utilizing native Paper DataComponents.
     *
     * @return binary inventory serializer
     */
    public static InventorySerializer binarySerializer() {
        return BinaryInventorySerializer.INSTANCE;
    }

    /**
     * Creates a standard storage-backed {@link InventoryModule}.
     *
     * @param plugin plugin instance
     * @param scheduler paper task scheduler
     * @param storage cotani storage instance
     * @return initialized inventory module
     */
    public static InventoryModule create(Plugin plugin, PaperTaskScheduler scheduler, CotaniStorage storage) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(storage, "storage");

        return builder(plugin, scheduler).storage(storage).build();
    }

    /**
     * Creates an inventory module backed by a custom repository.
     *
     * @param plugin plugin instance
     * @param scheduler paper task scheduler
     * @param repository custom inventory repository
     * @return initialized inventory module
     */
    public static InventoryModule create(Plugin plugin, PaperTaskScheduler scheduler, InventoryRepository repository) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(repository, "repository");

        return builder(plugin, scheduler).repository(repository).build();
    }

    /**
     * Creates a builder for flexible configuration of the {@link InventoryModule}.
     *
     * @param plugin plugin instance
     * @param scheduler paper task scheduler
     * @return module builder
     */
    public static Builder builder(Plugin plugin, PaperTaskScheduler scheduler) {
        return new Builder(plugin, scheduler);
    }

    /**
     * Builder for {@link InventoryModule}.
     */
    public static final class Builder {
        private final PaperTaskScheduler scheduler;
        private @Nullable CotaniStorage storage;
        private @Nullable InventoryRepository repository;
        private InventorySerializer serializer = BinaryInventorySerializer.INSTANCE;
        private CrossServerTransferLock transferLock = CrossServerTransferLock.noop();

        private Builder(Plugin plugin, PaperTaskScheduler scheduler) {
            Objects.requireNonNull(plugin, "plugin");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        }

        public Builder storage(CotaniStorage storage) {
            this.storage = Objects.requireNonNull(storage, "storage");
            return this;
        }

        public Builder repository(InventoryRepository repository) {
            this.repository = Objects.requireNonNull(repository, "repository");
            return this;
        }

        public Builder serializer(InventorySerializer serializer) {
            this.serializer = Objects.requireNonNull(serializer, "serializer");
            return this;
        }

        public Builder transferLock(CrossServerTransferLock transferLock) {
            this.transferLock = Objects.requireNonNull(transferLock, "transferLock");
            return this;
        }

        public InventoryModule build() {
            InventoryRepository effectiveRepository = repository;
            if (effectiveRepository == null) {
                if (storage == null) {
                    throw new IllegalStateException(
                            "Either storage or repository must be configured for InventoryModule");
                }
                effectiveRepository = new StorageInventoryRepository(storage, serializer);
            }

            InventorySyncService service =
                    new DefaultInventorySyncService(scheduler, effectiveRepository, transferLock);

            return new DefaultInventoryModule(service, effectiveRepository, serializer);
        }
    }
}
