package com.cotani.redis.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.ChannelSubscription;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.codec.RedisCodec;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RedisDistributedEventBusTest {

    private record TestPlayerEvent(String username) implements CotaniEvent {}

    @Test
    @SuppressWarnings("unchecked")
    void shouldPublishLocallyAndBroadcast() {
        var localBus = mock(EventBus.class);
        var redis = mock(CotaniRedis.class);
        var channel = mock(RedisChannel.class);
        var subMock = mock(ChannelSubscription.class);

        AtomicReference<Consumer<byte[]>> listenerRef = new AtomicReference<>();
        when(channel.subscribe(any())).thenAnswer(inv -> {
            listenerRef.set(inv.getArgument(0));
            return subMock;
        });

        when(redis.channel(any(), any())).thenReturn(channel);
        when(channel.publishAsync(any())).thenReturn(CompletableFuture.completedFuture(1L));

        var distBus = new RedisDistributedEventBus(localBus, redis, ChannelId.of("events:global"));
        distBus.registerCodec(
                TestPlayerEvent.class, RedisCodec.text(TestPlayerEvent::username, text -> new TestPlayerEvent(text)));

        var event = new TestPlayerEvent("Player1");
        when(localBus.publish(any())).thenReturn(event);

        var result = distBus.publish(event);
        assertEquals("Player1", result.username());
        verify(localBus).publish(event);
        verify(channel).publishAsync(any(byte[].class));

        distBus.close();
        verify(subMock).close();
        verify(localBus).close();
    }
}
