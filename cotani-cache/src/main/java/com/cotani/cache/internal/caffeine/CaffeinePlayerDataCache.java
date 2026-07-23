package com.cotani.cache.internal.caffeine;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.api.PlayerValueFactory;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;

/**
 * Thin adapter that delegates all operations to a {@link DataCache} keyed by {@link UUID}.
 *
 * @param <V> the player data type
 */
public final class CaffeinePlayerDataCache<V> implements PlayerDataCache<V> {

    private static final String PLAYER_PARAM = "player";

    private final DataCache<UUID, V> delegate;

    public CaffeinePlayerDataCache(
            DataCache<UUID, V> delegate,
            CacheRepository<UUID, V> repository,
            PlayerValueFactory<V> defaultValue,
            PaperTaskScheduler scheduler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public V get(Player player) {
        Objects.requireNonNull(player, PLAYER_PARAM);
        return get(player.getUniqueId());
    }

    @Override
    public V get(UUID uniqueId) {
        return delegate.get(uniqueId);
    }

    @Override
    public Optional<V> find(Player player) {
        Objects.requireNonNull(player, PLAYER_PARAM);
        return find(player.getUniqueId());
    }

    @Override
    public Optional<V> find(UUID uniqueId) {
        return delegate.find(uniqueId);
    }

    @Override
    public CompletionStage<V> getOrLoadAsync(UUID uniqueId) {
        return delegate.getOrLoad(uniqueId);
    }

    @Override
    public CompletionStage<V> loadAsync(UUID uniqueId) {
        return delegate.load(uniqueId);
    }

    @Override
    public CompletionStage<V> updateAsync(UUID uniqueId, UnaryOperator<V> updater) {
        return delegate.update(uniqueId, updater);
    }

    @Override
    public CompletionStage<V> mutateAsync(UUID uniqueId, Consumer<V> mutator) {
        return delegate.mutate(uniqueId, mutator);
    }

    @Override
    public CompletionStage<Void> saveAsync(UUID uniqueId) {
        return delegate.save(uniqueId);
    }

    @Override
    public CompletionStage<Void> saveDirty() {
        return delegate.saveDirty();
    }

    @Override
    public CompletionStage<Void> saveAll() {
        return delegate.saveAll();
    }

    @Override
    public void unload(Player player) {
        Objects.requireNonNull(player, PLAYER_PARAM);
        unload(player.getUniqueId());
    }

    @Override
    public void unload(UUID uniqueId) {
        delegate.unload(uniqueId);
    }

    @Override
    public boolean contains(Player player) {
        Objects.requireNonNull(player, PLAYER_PARAM);
        return contains(player.getUniqueId());
    }

    @Override
    public boolean contains(UUID uniqueId) {
        return delegate.contains(uniqueId);
    }

    @Override
    public void markDirty(Player player) {
        Objects.requireNonNull(player, PLAYER_PARAM);
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
}
