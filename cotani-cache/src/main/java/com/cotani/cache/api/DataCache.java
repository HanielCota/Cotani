package com.cotani.cache.api;

import com.cotani.cache.future.CacheFuture;
import com.cotani.cache.stats.CacheStatsView;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public interface DataCache<K, V> extends AutoCloseable {

    V get(K key);

    Optional<V> find(K key);

    CacheFuture<V> getOrLoad(K key);

    CacheFuture<V> load(K key);

    CacheFuture<V> update(K key, UnaryOperator<V> updater);

    CacheFuture<V> mutate(K key, Consumer<V> mutator);

    void put(K key, V value);

    CacheFuture<Void> save(K key);

    CacheFuture<Void> saveDirty();

    CacheFuture<Void> saveAll();

    void unload(K key);

    void invalidate(K key);

    boolean contains(K key);

    void markDirty(K key);

    int dirtyCount();

    long size();

    Map<K, V> snapshot();

    CacheStatsView stats();

    CompletionStage<Void> closeAsync();

    @Override
    void close();
}
