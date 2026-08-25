package com.cotani.inventory;

import com.cotani.AsyncCloseable;
import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySerializer;
import com.cotani.inventory.api.InventorySyncService;
import org.jspecify.annotations.NullMarked;

/**
 * Lifecycle and dependency container for the Cotani Inventory module.
 */
@NullMarked
public interface InventoryModule extends AsyncCloseable, AutoCloseable {

    /**
     * Returns the inventory synchronization and management service.
     *
     * @return sync service
     */
    InventorySyncService service();

    /**
     * Returns the underlying inventory persistence repository.
     *
     * @return repository
     */
    InventoryRepository repository();

    /**
     * Returns the binary snapshot serializer.
     *
     * @return serializer
     */
    InventorySerializer serializer();

    @Override
    default void close() {
        // The module does not own synchronous resources. Storage and scheduler lifecycles are
        // owned by their respective modules and are closed through closeAsync().
    }
}
