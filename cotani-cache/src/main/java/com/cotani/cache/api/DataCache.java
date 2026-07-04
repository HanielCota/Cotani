package com.cotani.cache.api;

import com.cotani.cache.stats.CacheStatsView;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Generic asynchronous cache for key-value pairs with persistence support.
 *
 * <p>All synchronous methods (get, find, put, unload, etc.) operate on the
 * in-memory cache and are safe to call from any thread. Async methods return
 * {@link CompletionStage} and may complete on a background executor.
 *
 * <p>Keys and values must not be {@code null}.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public interface DataCache<K, V> extends AutoCloseable {

    /**
     * Returns the cached value for the given key.
     *
     * @throws com.cotani.cache.exception.CacheException if the entry is not loaded
     */
    V get(K key);

    /**
     * Returns the cached value if present, empty otherwise.
     */
    Optional<V> find(K key);

    /**
     * Returns the cached value, loading from the repository if necessary.
     */
    CompletionStage<V> getOrLoad(K key);

    /**
     * Invalidates and reloads the entry from the repository.
     */
    CompletionStage<V> load(K key);

    /**
     * Atomically updates the entry using the provided function.
     *
     * @return the updated value
     */
    CompletionStage<V> update(K key, UnaryOperator<V> updater);

    /**
     * Mutates the entry in-place.
     *
     * @return the mutated value
     */
    CompletionStage<V> mutate(K key, Consumer<V> mutator);

    /**
     * Puts a value into the cache, replacing any existing entry.
     */
    void put(K key, V value);

    /**
     * Persists the entry to the repository.
     */
    CompletionStage<Void> save(K key);

    /**
     * Saves all dirty entries.
     */
    CompletionStage<Void> saveDirty();

    /**
     * Saves all cached entries.
     */
    CompletionStage<Void> saveAll();

    /**
     * Removes the entry from the cache without persisting.
     */
    void unload(K key);

    /**
     * Checks whether the cache contains an entry for the given key.
     */
    boolean contains(K key);

    /**
     * Marks the entry as dirty (pending save).
     */
    void markDirty(K key);

    /**
     * Returns the number of dirty entries.
     */
    int dirtyCount();

    /**
     * Returns the estimated number of entries.
     */
    long size();

    /**
     * Returns an immutable snapshot of cached values.
     */
    Map<K, V> snapshot();

    /**
     * Returns cache statistics.
     */
    CacheStatsView stats();

    /**
     * Gracefully closes the cache asynchronously, saving dirty entries.
     */
    CompletionStage<Void> closeAsync();

    /**
     * Synchronously closes the cache, blocking until all pending saves complete.
     */
    @Override
    void close();
}
