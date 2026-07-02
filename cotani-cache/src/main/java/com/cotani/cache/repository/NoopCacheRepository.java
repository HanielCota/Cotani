package com.cotani.cache.repository;

import com.cotani.cache.future.CacheFuture;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.Optional;

public final class NoopCacheRepository<K, V> implements CacheRepository<K, V> {

    private final PaperTaskScheduler scheduler;

    public NoopCacheRepository(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CacheFuture<Optional<V>> find(K key) {
        return CacheFuture.completed(Optional.empty(), scheduler);
    }

    @Override
    public CacheFuture<Void> save(K key, V value) {
        return CacheFuture.completed(null, scheduler);
    }

    @Override
    public CacheFuture<Void> delete(K key) {
        return CacheFuture.completed(null, scheduler);
    }
}
