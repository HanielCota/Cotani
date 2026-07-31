package com.cotani.cache.api;

import java.util.concurrent.CompletionStage;

/**
 * Persists cache state asynchronously on the cache-owned executor.
 *
 * <p>Cancellation is best effort and cannot roll back a repository write that has already
 * committed. Failures complete the returned stage exceptionally; callers must observe them.
 */
public interface CachePersistence<K> {
    CompletionStage<Void> save(K key);

    default CompletionStage<Void> saveAsync(K key) {
        return save(key);
    }

    CompletionStage<Void> saveDirty();

    default CompletionStage<Void> saveDirtyAsync() {
        return saveDirty();
    }

    CompletionStage<Void> saveAll();

    default CompletionStage<Void> saveAllAsync() {
        return saveAll();
    }
}
