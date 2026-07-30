package com.cotani.cache.internal.caffeine;

import com.cotani.cache.entry.CacheEntry;
import com.cotani.cache.exception.CacheLoadException;
import com.cotani.cache.repository.CacheRepository;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

final class CacheEntryLoader<K, V> {

    private final CacheRepository<K, V> repository;
    private final Function<K, V> defaultValue;
    private final Function<V, CacheEntry<V>> entryFactory;

    CacheEntryLoader(
            CacheRepository<K, V> repository, Function<K, V> defaultValue, Function<V, CacheEntry<V>> entryFactory) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.entryFactory = Objects.requireNonNull(entryFactory, "entryFactory");
    }

    CompletableFuture<CacheEntry<V>> load(K key) {
        return repository
                .find(key)
                .thenApply(optional -> optional.orElseGet(() -> defaultValue.apply(key)))
                .thenApply(value -> {
                    Objects.requireNonNull(value, "defaultValue must not return null");
                    return entryFactory.apply(value);
                })
                .toCompletableFuture()
                .exceptionally(throwable -> {
                    throw new CacheLoadException("Could not load cache entry: " + key, throwable);
                });
    }
}
