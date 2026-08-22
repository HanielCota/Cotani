package com.cotani.redis.cache;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.cache.invalidation.CacheInvalidation;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.ChannelSubscription;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.codec.RedisCodec;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RedisCacheInvalidationBusTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldPublishAndSubscribe() {
        var redis = mock(CotaniRedis.class);
        var channel = mock(RedisChannel.class);
        var channelId = ChannelId.of("cache:invalidation:player");

        when(redis.channel(any(), any())).thenReturn(channel);
        when(channel.publishAsync(any())).thenReturn(CompletableFuture.completedFuture(1L));

        var subMock = mock(ChannelSubscription.class);
        AtomicReference<Consumer<byte[]>> listenerRef = new AtomicReference<>();
        when(channel.subscribe(any())).thenAnswer(inv -> {
            listenerRef.set(inv.getArgument(0));
            return subMock;
        });

        var bus = RedisCacheInvalidationBus.of(redis, channelId, RedisCodec.string());

        var received = new AtomicReference<CacheInvalidation<String>>();
        var subscription = bus.subscribe(received::set);
        assertNotNull(subscription);

        var sourceId = UUID.randomUUID();
        bus.publish(new CacheInvalidation<>(sourceId, "user-123"))
                .toCompletableFuture()
                .join();
        verify(channel).publishAsync(any(byte[].class));

        subscription.close();
        verify(subMock).close();
    }
}
