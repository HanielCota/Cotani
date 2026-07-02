package com.cotani.cache.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;

public interface PlayerDataCache<V> extends AutoCloseable {

    V get(Player player);

    V get(UUID uniqueId);

    Optional<V> find(Player player);

    Optional<V> find(UUID uniqueId);

    CompletionStage<V> getOrLoadAsync(UUID uniqueId);

    CompletionStage<V> loadAsync(UUID uniqueId);

    CompletionStage<V> updateAsync(UUID uniqueId, UnaryOperator<V> updater);

    CompletionStage<V> mutateAsync(UUID uniqueId, Consumer<V> mutator);

    CompletionStage<Void> saveAsync(UUID uniqueId);

    CompletionStage<Void> saveDirty();

    CompletionStage<Void> saveAll();

    /**
     * Unloads the cached entry for the given player.
     *
     * <p>Must be called from the server main thread.
     */
    void unload(Player player);

    void unload(UUID uniqueId);

    /**
     * Checks whether the cache contains an entry for the given player.
     *
     * <p>Must be called from the server main thread.
     */
    boolean contains(Player player);

    boolean contains(UUID uniqueId);

    /**
     * Marks the entry for the given player as dirty.
     *
     * <p>Must be called from the server main thread.
     */
    void markDirty(Player player);

    void markDirty(UUID uniqueId);

    int dirtyCount();

    long size();

    CompletionStage<Void> closeAsync();

    @Override
    void close();
}
