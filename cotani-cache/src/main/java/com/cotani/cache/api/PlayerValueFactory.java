package com.cotani.cache.api;

import java.util.UUID;

/**
 * Factory for creating default player values when no persisted data exists.
 *
 * <p>The provided UUID is the player's unique identifier and must be used to
 * create a value associated with that specific player.
 */
@FunctionalInterface
public interface PlayerValueFactory<V> {

    /**
     * Creates a default value for the given player.
     *
     * @param uniqueId the player's UUID, never null
     * @return a new non-null value instance
     */
    V create(UUID uniqueId);
}
