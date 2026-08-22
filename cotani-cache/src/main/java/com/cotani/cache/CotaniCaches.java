package com.cotani.cache;

import com.cotani.cache.builder.DataCacheBuilder;
import com.cotani.cache.builder.PlayerDataCacheBuilder;
import java.time.Duration;

/**
 * Entrypoint factory for creating cache builders.
 */
public final class CotaniCaches {

    private CotaniCaches() {}

    /**
     * Creates a builder for a generic data cache.
     *
     * @param keyType key class token
     * @param valueType value class token
     * @param <K> key type
     * @param <V> value type
     * @return cache builder
     */
    public static <K, V> DataCacheBuilder<K, V> data(Class<K> keyType, Class<V> valueType) {
        return CotaniCache.data(keyType, valueType);
    }

    /**
     * Creates a builder for a player data cache.
     *
     * @param valueType value class token
     * @param <V> value type
     * @return player cache builder
     */
    public static <V> PlayerDataCacheBuilder<V> players(Class<V> valueType) {
        return CotaniCache.players(valueType);
    }

    /**
     * Creates a temporary cache builder with the given expiration.
     *
     * @param keyType key class token
     * @param valueType value class token
     * @param duration expiration duration
     * @param <K> key type
     * @param <V> value type
     * @return cache builder
     */
    public static <K, V> DataCacheBuilder<K, V> temporary(Class<K> keyType, Class<V> valueType, Duration duration) {
        return CotaniCache.temporary(keyType, valueType, duration);
    }
}
