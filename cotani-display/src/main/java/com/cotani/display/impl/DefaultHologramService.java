package com.cotani.display.impl;

import com.cotani.api.InternalApi;
import com.cotani.display.api.Hologram;
import com.cotani.display.api.HologramBuilder;
import com.cotani.display.api.HologramService;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@InternalApi
public final class DefaultHologramService implements HologramService {

    private static final String HOLOGRAM_NOT_NULL = "hologram cannot be null";
    private static final String ENTITY_ID_NOT_NULL = "entityId cannot be null";
    private static final String NAME_NOT_NULL = "name cannot be null";
    private static final String ID_NOT_NULL = "id cannot be null";

    private final PaperTaskScheduler scheduler;
    private final Map<UUID, Hologram> hologramsById = new ConcurrentHashMap<>();
    private final Map<String, Hologram> hologramsByName = new ConcurrentHashMap<>();
    private final Map<UUID, Hologram> hologramsByEntityId = new ConcurrentHashMap<>();

    public DefaultHologramService(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
    }

    public void register(Hologram hologram) {
        Objects.requireNonNull(hologram, HOLOGRAM_NOT_NULL);
        hologramsById.put(hologram.id(), hologram);
        hologram.name().ifPresent(name -> hologramsByName.put(name.toLowerCase(Locale.ROOT), hologram));
        for (UUID entityId : hologram.entityIds()) {
            hologramsByEntityId.put(entityId, hologram);
        }
    }

    public void bindEntity(UUID entityId, Hologram hologram) {
        Objects.requireNonNull(entityId, ENTITY_ID_NOT_NULL);
        Objects.requireNonNull(hologram, HOLOGRAM_NOT_NULL);
        hologramsByEntityId.put(entityId, hologram);
    }

    public void unbindEntity(UUID entityId) {
        Objects.requireNonNull(entityId, ENTITY_ID_NOT_NULL);
        hologramsByEntityId.remove(entityId);
    }

    @Override
    public HologramBuilder builder() {
        return new DefaultHologramBuilder(this, scheduler);
    }

    @Override
    public HologramBuilder builder(String name) {
        return new DefaultHologramBuilder(this, scheduler, name);
    }

    @Override
    public Optional<Hologram> find(String name) {
        Objects.requireNonNull(name, NAME_NOT_NULL);
        return Optional.ofNullable(hologramsByName.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<Hologram> find(UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL);
        return Optional.ofNullable(hologramsById.get(id));
    }

    @Override
    public Optional<Hologram> findByEntityId(UUID entityId) {
        Objects.requireNonNull(entityId, ENTITY_ID_NOT_NULL);
        var direct = hologramsByEntityId.get(entityId);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (Hologram hologram : hologramsById.values()) {
            if (hologram.entityIds().contains(entityId)) {
                hologramsByEntityId.put(entityId, hologram);
                return Optional.of(hologram);
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<Hologram> all() {
        return List.copyOf(hologramsById.values());
    }

    @Override
    public CompletionStage<Void> removeAsync(Hologram hologram) {
        Objects.requireNonNull(hologram, HOLOGRAM_NOT_NULL);
        hologramsById.remove(hologram.id());
        hologram.name().ifPresent(name -> hologramsByName.remove(name.toLowerCase(Locale.ROOT)));
        for (UUID entityId : hologram.entityIds()) {
            hologramsByEntityId.remove(entityId);
        }
        return hologram.destroyAsync();
    }

    @Override
    public CompletionStage<Void> removeAsync(String name) {
        Objects.requireNonNull(name, NAME_NOT_NULL);
        var hologram = hologramsByName.remove(name.toLowerCase(Locale.ROOT));
        if (hologram == null) {
            return CompletableFuture.completedFuture(null);
        }
        hologramsById.remove(hologram.id());
        for (UUID entityId : hologram.entityIds()) {
            hologramsByEntityId.remove(entityId);
        }
        return hologram.destroyAsync();
    }

    @Override
    public CompletionStage<Void> removeAsync(UUID id) {
        Objects.requireNonNull(id, ID_NOT_NULL);
        var hologram = hologramsById.remove(id);
        if (hologram == null) {
            return CompletableFuture.completedFuture(null);
        }
        hologram.name().ifPresent(name -> hologramsByName.remove(name.toLowerCase(Locale.ROOT)));
        for (UUID entityId : hologram.entityIds()) {
            hologramsByEntityId.remove(entityId);
        }
        return hologram.destroyAsync();
    }

    @Override
    public CompletionStage<Void> clearAsync() {
        var snapshot = List.copyOf(hologramsById.values());
        hologramsById.clear();
        hologramsByName.clear();
        hologramsByEntityId.clear();

        var futures = snapshot.stream()
                .map(Hologram::destroyAsync)
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture<?>[]::new);

        return CompletableFuture.allOf(futures);
    }
}
