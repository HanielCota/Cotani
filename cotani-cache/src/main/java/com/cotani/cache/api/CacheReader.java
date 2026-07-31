package com.cotani.cache.api;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Read-only view of an asynchronous cache. Keys and returned values are never {@code null}.
 *
 * <p>In-memory methods run on the calling thread. Loading uses the cache-owned executor and the
 * returned stage may complete on that executor; repository implementations must not access
 * Bukkit/Paper state.
 */
public interface CacheReader<K, V> {
    V get(K key);

    Optional<V> find(K key);

    CompletionStage<V> getOrLoad(K key);

    default CompletionStage<V> getOrLoadAsync(K key) {
        return getOrLoad(key);
    }

    CompletionStage<V> load(K key);

    default CompletionStage<V> loadAsync(K key) {
        return load(key);
    }
}
