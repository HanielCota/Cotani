package com.cotani.display.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Service managing the lifecycle, registration, and discovery of {@link Hologram} instances.
 */
public interface HologramService {

    /**
     * Creates a new fluent {@link HologramBuilder}.
     *
     * @return a new builder
     */
    HologramBuilder builder();

    /**
     * Creates a new fluent {@link HologramBuilder} with a predefined name.
     *
     * @param name the unique name
     * @return a new builder
     */
    HologramBuilder builder(String name);

    /**
     * Finds a registered hologram by its unique custom name.
     *
     * @param name the name
     * @return the optional hologram
     */
    Optional<Hologram> find(String name);

    /**
     * Finds a registered hologram by its UUID.
     *
     * @param id the hologram UUID
     * @return the optional hologram
     */
    Optional<Hologram> find(UUID id);

    /**
     * Finds a hologram containing an entity with the specified UUID.
     *
     * @param entityId the Bukkit entity UUID
     * @return the optional hologram
     */
    Optional<Hologram> findByEntityId(UUID entityId);

    /**
     * Returns an immutable snapshot of all registered holograms.
     *
     * @return all holograms
     */
    Collection<Hologram> all();

    /**
     * Removes and despawns the specified hologram.
     *
     * @param hologram the hologram to remove
     * @return a completion stage
     */
    CompletionStage<Void> removeAsync(Hologram hologram);

    /**
     * Removes and despawns a hologram by its name.
     *
     * @param name the name
     * @return a completion stage
     */
    CompletionStage<Void> removeAsync(String name);

    /**
     * Removes and despawns a hologram by its UUID.
     *
     * @param id the hologram UUID
     * @return a completion stage
     */
    CompletionStage<Void> removeAsync(UUID id);

    /**
     * Despawns and unregisters all holograms.
     *
     * @return a completion stage
     */
    CompletionStage<Void> clearAsync();
}
