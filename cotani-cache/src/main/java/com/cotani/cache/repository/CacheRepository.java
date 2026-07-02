package com.cotani.cache.repository;

import com.cotani.cache.future.CacheFuture;
import java.util.Optional;

/**
 * Repository for cache persistence.
 *
 * <p>All methods return a {@link CacheFuture} that may complete asynchronously.
 * Implementations must not access the Bukkit/Paper API from async threads.
 */
public interface CacheRepository<K, V> {

    CacheFuture<Optional<V>> find(K key);

    CacheFuture<Void> save(K key, V value);

    CacheFuture<Void> delete(K key);
}
