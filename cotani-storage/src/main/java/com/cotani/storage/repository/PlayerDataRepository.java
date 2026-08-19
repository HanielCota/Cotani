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

    public CompletionStage<T> findOrCreateAsync(UUID playerId, String name) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");

        return findByIdAsync(playerId)
                .thenCompose(optional ->
                        optional.map(CompletableFuture::completedStage).orElseGet(() -> createAsync(playerId, name)));
    }

    /** @deprecated use {@link #findOrCreateAsync(UUID, String)} */
    @Deprecated(forRemoval = false)
    @SuppressWarnings("InlineMeSuggester")
    public CompletionStage<T> findOrCreate(UUID playerId, String name) {
        return findOrCreateAsync(playerId, name);
    }

    protected CompletionStage<T> createAsync(UUID playerId, String name) {
        return create(playerId, name);
    }

    protected abstract CompletionStage<T> create(UUID playerId, String name);
}
