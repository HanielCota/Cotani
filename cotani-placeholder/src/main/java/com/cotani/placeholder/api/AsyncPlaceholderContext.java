package com.cotani.placeholder.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Immutable context available to asynchronous placeholder handlers.
 *
 * <p>This type intentionally exposes no Bukkit objects or player-resolution methods. Async
 * handlers must use immutable identifiers and perform any external work without touching Bukkit.
 */
@NullMarked
public record AsyncPlaceholderContext(
        @Nullable UUID viewerId, @Nullable UUID targetId, Map<String, Object> parameters) {

    public AsyncPlaceholderContext {
        Objects.requireNonNull(parameters, "Parameter 'parameters' must not be null");
        parameters.forEach((key, value) -> {
            Objects.requireNonNull(key, "Placeholder parameter key must not be null");
            Objects.requireNonNull(value, "Placeholder parameter value must not be null");
            PlaceholderContext.ensureAsyncSafeValue(value);
        });
        parameters = Map.copyOf(parameters);
    }

    /**
     * Creates an async-safe view of a synchronous context.
     *
     * @param context source context
     * @return immutable async context
     */
    public static AsyncPlaceholderContext from(PlaceholderContext context) {
        Objects.requireNonNull(context, "Parameter 'context' must not be null");
        return new AsyncPlaceholderContext(context.viewerId(), context.targetId(), context.parameters());
    }

    /**
     * Retrieves a custom parameter by key.
     *
     * @param key parameter key
     * @param <T> value type
     * @return parameter value if present
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> parameter(String key) {
        Objects.requireNonNull(key, "Parameter 'key' must not be null");
        return Optional.ofNullable((T) parameters.get(key));
    }
}
