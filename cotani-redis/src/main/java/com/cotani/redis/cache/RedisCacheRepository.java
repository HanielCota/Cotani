package com.cotani.redis.cache;

import com.cotani.cache.repository.CacheRepository;
import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.store.RedisKey;
import com.cotani.redis.store.RedisKeyValueStore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Adapter integrating {@link RedisKeyValueStore} as a persistent backing store for {@link CacheRepository}.
 *
 * @param <K> cache key type
 * @param <V> cache value type
 */
public final class RedisCacheRepository<K, V> implements CacheRepository<K, V> {

    private final RedisKeyValueStore store;
    private final Function<K, RedisKey> keyMapper;
    private final RedisCodec<V> codec;

    private RedisCacheRepository(RedisKeyValueStore store, Function<K, RedisKey> keyMapper, RedisCodec<V> codec) {
        this.store = Objects.requireNonNull(store, "store");
        this.keyMapper = Objects.requireNonNull(keyMapper, "keyMapper");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public static <K, V> RedisCacheRepository<K, V> of(
            RedisKeyValueStore store, Function<K, RedisKey> keyMapper, RedisCodec<V> codec) {
        return new RedisCacheRepository<>(store, keyMapper, codec);
    }

    public static <V> RedisCacheRepository<String, V> ofPrefix(
            RedisKeyValueStore store, String prefix, RedisCodec<V> codec) {
        Objects.requireNonNull(prefix, "prefix");
        return new RedisCacheRepository<>(store, key -> RedisKey.of(prefix + ":" + key), codec);
    }

    @Override
    public CompletionStage<Optional<V>> find(K key) {
        Objects.requireNonNull(key, "key");
        RedisKey redisKey = Objects.requireNonNull(keyMapper.apply(key), "redisKey");
        return store.getAsync(redisKey, codec);
    }

    @Override
    public CompletionStage<Void> save(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        RedisKey redisKey = Objects.requireNonNull(keyMapper.apply(key), "redisKey");
        return store.setAsync(redisKey, value, codec);
    }

    @Override
    public CompletionStage<Void> delete(K key) {
        Objects.requireNonNull(key, "key");
        RedisKey redisKey = Objects.requireNonNull(keyMapper.apply(key), "redisKey");
        return store.deleteAsync(redisKey).thenApply(_ -> (Void) null);
    }
}
