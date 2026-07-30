package com.cotani.cache.api;

import com.cotani.AsyncCloseable;
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
 * <p>Without a configured {@code CacheInvalidationBus}, consistency is local and eventual. With a
 * shared bus, clean entries are invalidated after successful writes. Every out-of-process writer
 * must participate in that protocol; dirty local entries are deliberately retained to avoid data
 * loss.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
@SuppressWarnings("MissingOverride") // Keep declarations on the compatibility facade's binary surface.
public interface DataCache<K, V>
        extends CacheReader<K, V>,
                CacheMutator<K, V>,
                CachePersistence<K>,
                CacheDiagnostics<K, V>,
                AsyncCloseable,
                AutoCloseable {

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

    /** Async-suffixed alias for {@link #getOrLoad(Object)}. */
    default CompletionStage<V> getOrLoadAsync(K key) {
        return getOrLoad(key);
    }

    /**
     * Invalidates and reloads the entry from the repository.
     */
    CompletionStage<V> load(K key);

    /** Async-suffixed alias for {@link #load(Object)}. */
    default CompletionStage<V> loadAsync(K key) {
        return load(key);
    }

    /**
     * Atomically updates the entry using the provided function.
     *
     * @return the updated value
     */
    CompletionStage<V> update(K key, UnaryOperator<V> updater);

    /** Async-suffixed alias for {@link #update(Object, UnaryOperator)}. */
    default CompletionStage<V> updateAsync(K key, UnaryOperator<V> updater) {
        return update(key, updater);
    }

    /**
     * Mutates the entry in-place.
     *
     * @return the mutated value
     */
    CompletionStage<V> mutate(K key, Consumer<V> mutator);

    /** Async-suffixed alias for {@link #mutate(Object, Consumer)}. */
    default CompletionStage<V> mutateAsync(K key, Consumer<V> mutator) {
        return mutate(key, mutator);
    }

    /**
     * Puts a value into the cache, replacing any existing entry.
     */
    void put(K key, V value);

    /**
     * Persists the entry to the repository.
     */
    CompletionStage<Void> save(K key);

    /** Async-suffixed alias for {@link #save(Object)}. */
    default CompletionStage<Void> saveAsync(K key) {
        return save(key);
    }

    /**
     * Saves all dirty entries.
     */
    CompletionStage<Void> saveDirty();

    /** Async-suffixed alias for {@link #saveDirty()}. */
    default CompletionStage<Void> saveDirtyAsync() {
        return saveDirty();
    }

    /**
     * Saves all cached entries.
     */
    CompletionStage<Void> saveAll();

    /** Async-suffixed alias for {@link #saveAll()}. */
    default CompletionStage<Void> saveAllAsync() {
        return saveAll();
    }

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
    @Override
    CompletionStage<Void> closeAsync();

    /**
     * Synchronously closes the cache, blocking until all pending saves complete.
     */
    @Override
    void close();
}
