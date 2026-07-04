package com.cotani.cache;

import com.cotani.cache.builder.DataCacheBuilder;
import com.cotani.cache.builder.PlayerDataCacheBuilder;
import com.cotani.cache.policy.CachePreset;
import java.time.Duration;

/**
 * Factory for creating cache builders.
 *
 * <p>All methods are static and return pre-configured builder instances.
 * No instantiation required.
 */
public final class CotaniCache {

    private CotaniCache() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Creates a builder for a generic data cache.
     *
     * @param keyType   the key class token
     * @param valueType the value class token
     * @param <K>       the key type
     * @param <V>       the value type
     * @return a new builder
     */
    public static <K, V> DataCacheBuilder<K, V> data(Class<K> keyType, Class<V> valueType) {
        return new DataCacheBuilder<>(keyType, valueType);
    }

    /**
     * Creates a builder for a player data cache.
     *
     * @param valueType the value class token
     * @param <V>       the value type
     * @return a new builder
     */
    public static <V> PlayerDataCacheBuilder<V> players(Class<V> valueType) {
        return new PlayerDataCacheBuilder<>(valueType);
    }

    /**
     * Creates a temporary cache builder with the given expiration.
     *
     * @param keyType   the key class token
     * @param valueType the value class token
     * @param duration  how long entries remain valid after write
     * @param <K>       the key type
     * @param <V>       the value type
     * @return a builder pre-configured with {@link CachePreset#TEMPORARY}
     */
    public static <K, V> DataCacheBuilder<K, V> temporary(Class<K> keyType, Class<V> valueType, Duration duration) {
        return data(keyType, valueType).preset(CachePreset.TEMPORARY).expireAfterWrite(duration);
    }
}
