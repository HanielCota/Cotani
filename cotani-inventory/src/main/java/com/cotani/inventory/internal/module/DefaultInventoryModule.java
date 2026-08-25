package com.cotani.inventory.internal.module;

import com.cotani.api.InternalApi;
import com.cotani.inventory.InventoryModule;
import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySerializer;
import com.cotani.inventory.api.InventorySyncService;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

@InternalApi
@NullMarked
public final class DefaultInventoryModule implements InventoryModule {

    private final InventorySyncService service;
    private final InventoryRepository repository;
    private final InventorySerializer serializer;

    public DefaultInventoryModule(
            InventorySyncService service, InventoryRepository repository, InventorySerializer serializer) {
        this.service = Objects.requireNonNull(service, "service");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    @Override
    public InventorySyncService service() {
        return service;
    }

    @Override
    public InventoryRepository repository() {
        return repository;
    }

    @Override
    public InventorySerializer serializer() {
        return serializer;
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return CompletableFuture.completedFuture(null);
    }
}
