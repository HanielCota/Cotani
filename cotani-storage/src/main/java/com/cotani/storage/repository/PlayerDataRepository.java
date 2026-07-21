package com.cotani.storage.repository;

import com.cotani.storage.api.CotaniStorage;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public abstract class PlayerDataRepository<T> extends CrudRepository<UUID, T> {

    protected PlayerDataRepository(CotaniStorage storage) {
        super(storage);
    }

    public CompletionStage<T> findOrCreate(UUID playerId, String name) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");
        return findById(playerId)
                .thenCompose(optional ->
                        optional.map(CompletableFuture::completedStage).orElseGet(() -> create(playerId, name)));
    }

    protected abstract CompletionStage<T> create(UUID playerId, String name);
}
