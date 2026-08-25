package com.cotani.redis.cache;

import com.cotani.cache.invalidation.CacheInvalidation;
import com.cotani.cache.invalidation.CacheInvalidationBus;
import com.cotani.cache.invalidation.CacheInvalidationSubscription;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.codec.ByteArrayRedisCodec;
import com.cotani.redis.codec.RedisCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Distributed cache invalidation bus backed by Redis Pub/Sub.
 *
 * <p>Enables cross-server L1 cache invalidation when shared L2 data is updated.
 *
 * @param <K> cache key type
 */
public final class RedisCacheInvalidationBus<K> implements CacheInvalidationBus<K> {

    private final RedisChannel<byte[]> rawChannel;
    private final RedisCodec<K> keyCodec;

    public RedisCacheInvalidationBus(CotaniRedis redis, ChannelId channelId, RedisCodec<K> keyCodec) {
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(channelId, "channelId");
        this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec");
        this.rawChannel = redis.channel(channelId, ByteArrayRedisCodec.INSTANCE);
    }

    public static <K> RedisCacheInvalidationBus<K> of(CotaniRedis redis, ChannelId channelId, RedisCodec<K> keyCodec) {
        return new RedisCacheInvalidationBus<>(redis, channelId, keyCodec);
    }

    @Override
    public CacheInvalidationSubscription subscribe(Consumer<CacheInvalidation<K>> listener) {
        Objects.requireNonNull(listener, "listener");
        var subscription = rawChannel.subscribe(bytes -> {
            var invalidation = decode(bytes);
            if (invalidation != null) {
                listener.accept(invalidation);
            }
        });
        return subscription::close;
    }

    @Override
    public CompletionStage<Void> publish(CacheInvalidation<K> invalidation) {
        Objects.requireNonNull(invalidation, "invalidation");
        byte[] payload = encode(invalidation);
        return rawChannel.publishAsync(payload).thenApply(_ -> null);
    }

    private byte[] encode(CacheInvalidation<K> invalidation) {
        try {
            var baos = new ByteArrayOutputStream();
            var dos = new DataOutputStream(baos);
            dos.writeLong(invalidation.sourceId().getMostSignificantBits());
            dos.writeLong(invalidation.sourceId().getLeastSignificantBits());
            byte[] keyBytes = Objects.requireNonNull(keyCodec.encode(invalidation.key()), "encoded key");
            dos.writeInt(keyBytes.length);
            dos.write(keyBytes);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode cache invalidation payload", e);
        }
    }

    private @Nullable CacheInvalidation<K> decode(byte[] bytes) {
        if (bytes == null || bytes.length < 20) {
            return null;
        }
        try {
            var bais = new ByteArrayInputStream(bytes);
            var dis = new DataInputStream(bais);
            long most = dis.readLong();
            long least = dis.readLong();
            UUID sourceId = new UUID(most, least);
            int len = dis.readInt();
            byte[] keyBytes = new byte[len];
            dis.readFully(keyBytes);
            K key = keyCodec.decode(keyBytes);
            if (key == null) {
                return null;
            }
            return new CacheInvalidation<>(sourceId, key);
        } catch (Exception exception) {
            java.util.logging.Logger.getLogger(RedisCacheInvalidationBus.class.getName())
                    .log(java.util.logging.Level.FINE, "Could not decode Redis cache invalidation", exception);
            return null;
        }
    }
}
