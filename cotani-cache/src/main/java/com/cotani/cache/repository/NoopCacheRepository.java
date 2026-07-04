package com.cotani.cache.repository;

import com.cotani.task.util.CompletionStages;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * No-op repository that performs no persistence.
 *
 * <p>All operations complete immediately with empty or null results.
 * Useful for caches that only need in-memory storage.
 */
@SuppressWarnings("unused")
public final class NoopCacheRepository<K, V> implements CacheRepository<K, V> {

    @Override
    public CompletionStage<Optional<V>> find(K key) {
        return CompletableFuture.completedStage(Optional.empty());
    }

    @Override
    public CompletionStage<Void> save(K key, V value) {
        return CompletionStages.completedVoid();
    }

    @Override
    public CompletionStage<Void> delete(K key) {
        return CompletionStages.completedVoid();
    }
}
