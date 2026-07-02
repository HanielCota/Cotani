package com.cotani.cache.internal.caffeine;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.api.PlayerValueFactory;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;

public final class CaffeinePlayerDataCache<V> implements PlayerDataCache<V> {

    private final DataCache<UUID, V> delegate;
    private final CacheRepository<UUID, V> repository;
    private final PlayerValueFactory<V> defaultValue;
    private final ConcurrentHashMap<UUID, CompletionStage<V>> loading = new ConcurrentHashMap<>();

    public CaffeinePlayerDataCache(
            DataCache<UUID, V> delegate,
            CacheRepository<UUID, V> repository,
            PlayerValueFactory<V> defaultValue,
            PaperTaskScheduler scheduler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public V get(Player player) {
        return get(player.getUniqueId());
    }

    @Override
    public V get(UUID uniqueId) {
        return delegate.get(uniqueId);
    }

    @Override
    public Optional<V> find(Player player) {
        return find(player.getUniqueId());
    }

    @Override
    public Optional<V> find(UUID uniqueId) {
        return delegate.find(uniqueId);
    }

    @Override
    public CompletionStage<V> getOrLoadAsync(UUID uniqueId) {
        var existing = delegate.find(uniqueId);
        if (existing.isPresent()) {
            return CompletableFuture.completedFuture(existing.get());
        }
        return loadAsync(uniqueId);
    }

    @Override
    public CompletionStage<V> loadAsync(UUID uniqueId) {
        return loading.computeIfAbsent(uniqueId, this::loadInternalAsync);
    }

    @Override
    public CompletionStage<V> updateAsync(UUID uniqueId, UnaryOperator<V> updater) {
        return delegate.update(uniqueId, updater).toCompletionStage();
    }

    @Override
    public CompletionStage<V> mutateAsync(UUID uniqueId, Consumer<V> mutator) {
        return delegate.mutate(uniqueId, mutator).toCompletionStage();
    }

    @Override
    public CompletionStage<Void> saveAsync(UUID uniqueId) {
        return delegate.save(uniqueId).toCompletionStage();
    }

    @Override
    public CompletionStage<Void> saveDirty() {
        return delegate.saveDirty().toCompletionStage();
    }

    @Override
    public CompletionStage<Void> saveAll() {
        return delegate.saveAll().toCompletionStage();
    }

    @Override
    public void unload(Player player) {
        unload(player.getUniqueId());
    }

    @Override
    public void unload(UUID uniqueId) {
        delegate.unload(uniqueId);
    }

    @Override
    public boolean contains(Player player) {
        return contains(player.getUniqueId());
    }

    @Override
    public boolean contains(UUID uniqueId) {
        return delegate.contains(uniqueId);
    }

    @Override
    public void markDirty(Player player) {
        markDirty(player.getUniqueId());
    }

    @Override
    public void markDirty(UUID uniqueId) {
        delegate.markDirty(uniqueId);
    }

    @Override
    public int dirtyCount() {
        return delegate.dirtyCount();
    }

    @Override
    public long size() {
        return delegate.size();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private CompletionStage<V> loadInternalAsync(UUID uniqueId) {
        var future = repository
                .find(uniqueId)
                .toCompletionStage()
                .thenApply(optional -> optional.orElseGet(() -> defaultValue.create(uniqueId)))
                .thenApply(value -> {
                    delegate.put(uniqueId, value);
                    return value;
                })
                .whenComplete((_, _) -> loading.remove(uniqueId));

        return future;
    }
}
