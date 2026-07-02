package com.cotani.cache;

import com.cotani.cache.builder.DataCacheBuilder;
import com.cotani.cache.builder.PlayerDataCacheBuilder;
import com.cotani.cache.policy.CachePreset;
import java.time.Duration;

public final class CotaniCache {

    private CotaniCache() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    public static <K, V> DataCacheBuilder<K, V> data(Class<K> keyType, Class<V> valueType) {
        return new DataCacheBuilder<>(keyType, valueType);
    }

    public static <V> PlayerDataCacheBuilder<V> players(Class<V> valueType) {
        return new PlayerDataCacheBuilder<>(valueType);
    }

    public static <K, V> DataCacheBuilder<K, V> temporary(Class<K> keyType, Class<V> valueType, Duration duration) {
        return data(keyType, valueType).preset(CachePreset.TEMPORARY).expireAfterWrite(duration);
    }
}
