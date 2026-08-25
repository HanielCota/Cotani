package com.cotani.placeholder.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Immutable context used for placeholder resolution.
 *
 * <p>Captures only immutable player identifiers and parameter data. It deliberately never stores
 * live Bukkit objects, so the context can safely be passed through asynchronous pipelines.
 */
@NullMarked
public record PlaceholderContext(
        @Nullable UUID viewerId, @Nullable UUID targetId, Map<String, Object> parameters) {

    public PlaceholderContext {
        Objects.requireNonNull(parameters, "Parameter 'parameters' must not be null");
        parameters.forEach((key, value) -> {
            Objects.requireNonNull(key, "Placeholder parameter key must not be null");
            Objects.requireNonNull(value, "Placeholder parameter value must not be null");
            ensureAsyncSafeValue(value);
        });
        parameters = Map.copyOf(parameters);
    }

    /**
     * Creates an empty placeholder context with no viewer or target.
     *
     * @return empty context
     */
    public static PlaceholderContext empty() {
        return new PlaceholderContext(null, null, Map.of());
    }

    /**
     * Creates a placeholder context bound to the given player UUID.
     *
     * @param playerId player UUID
     * @return context bound to playerId
     */
    public static PlaceholderContext of(@Nullable UUID playerId) {
        return new PlaceholderContext(playerId, null, Map.of());
    }

    /**
     * Creates a relational placeholder context between a viewer UUID and a target UUID.
     *
     * @param viewerId viewer UUID
     * @param targetId target UUID
     * @return relational context
     */
    public static PlaceholderContext relational(UUID viewerId, UUID targetId) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        Objects.requireNonNull(targetId, "Parameter 'targetId' must not be null");
        return new PlaceholderContext(viewerId, targetId, Map.of());
    }

    /**
     * Returns a new context with the given parameter appended or overwritten.
     *
     * @param key parameter key
     * @param value parameter value
     * @return updated context
     */
    public PlaceholderContext with(String key, Object value) {
        Objects.requireNonNull(key, "Parameter 'key' must not be null");
        Objects.requireNonNull(value, "Parameter 'value' must not be null");

        var newParams = new java.util.HashMap<>(parameters);
        newParams.put(key, value);
        return new PlaceholderContext(viewerId, targetId, newParams);
    }

    /**
     * Resolves the primary player from its identifier.
     *
     * <p>This method is for synchronous placeholder resolution on the owning server thread only.
     * Asynchronous expansions must use {@link #viewerId()} and {@link #targetId()} and must not
     * call Bukkit APIs.
     *
     * @return online player optional
     */
    public Optional<Player> player() {
        return viewer().or(this::target);
    }

    /**
     * Resolves the viewer player from its identifier.
     *
     * <p>This method is synchronous and must only be called on the owning server thread.
     *
     * @return online viewer optional
     */
    public Optional<Player> viewer() {
        if (viewerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getPlayer(viewerId));
    }

    /**
     * Resolves the target player from its identifier.
     *
     * <p>This method is synchronous and must only be called on the owning server thread.
     *
     * @return online target optional
     */
    public Optional<Player> target() {
        if (targetId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getPlayer(targetId));
    }

    /**
     * Retrieves a custom parameter by key.
     *
     * @param key parameter key
     * @param <T> value type
     * @return optional parameter value
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> parameter(String key) {
        Objects.requireNonNull(key, "Parameter 'key' must not be null");
        return Optional.ofNullable((T) parameters.get(key));
    }

    static void ensureAsyncSafeValue(Object value) {
        if (value instanceof Entity
                || value instanceof World
                || value instanceof Inventory
                || value instanceof Block
                || value instanceof Location) {
            throw new IllegalArgumentException("Bukkit objects cannot be stored in PlaceholderContext");
        }
    }
}
