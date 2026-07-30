package com.cotani.cache.api;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Mutates entries already owned by a cache instance.
 *
 * <p>Keys, values and mutation callbacks must be non-null. Implementations serialize mutation of
 * one entry and reject new mutation after closing begins.
 */
public interface CacheMutator<K, V> {

    CompletionStage<V> update(K key, UnaryOperator<V> updater);

    default CompletionStage<V> updateAsync(K key, UnaryOperator<V> updater) {
        return update(key, updater);
    }

    CompletionStage<V> mutate(K key, Consumer<V> mutator);

    default CompletionStage<V> mutateAsync(K key, Consumer<V> mutator) {
        return mutate(key, mutator);
    }

    void put(K key, V value);

    void unload(K key);

    void markDirty(K key);
}
