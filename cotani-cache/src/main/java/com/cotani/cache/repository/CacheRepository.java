package com.cotani.cache.repository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Repository for cache persistence.
 *
 * <p>All methods return a {@link CompletionStage} that may complete asynchronously.
 * Implementations must not access the Bukkit/Paper API from async threads.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public interface CacheRepository<K, V> {

    /**
     * Finds a persisted value by key.
     *
     * @param key the lookup key
     * @return a stage that completes with a non-null Optional containing the value, or
     *         {@link Optional#empty()} if not found
     */
    CompletionStage<Optional<V>> find(K key);

    /**
     * Persists a key-value pair.
     *
     * @param key   the key
     * @param value the value to persist
     * @return a non-null stage that completes when the save is done
     */
    CompletionStage<Void> save(K key, V value);

    /**
     * Deletes a persisted entry.
     *
     * @param key the key to delete
     * @return a non-null stage that completes when the deletion is done
     */
    CompletionStage<Void> delete(K key);
}
