package com.cotani.redis.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.codec.RedisCodec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DefaultRedisChannelTest {

    @Test
    void shouldPublishAndSubscribeMessages() {
        var id = ChannelId.of("network:chat");
        var subscribedConsumer = new ArrayList<Consumer<byte[]>>();
        var unsubscribed = new AtomicBoolean();

        var channel = new DefaultRedisChannel<String>(
                id,
                RedisCodec.string(),
                (channelId, bytes) -> {
                    assertEquals(id, channelId);
                    assertEquals("Hello", new String(bytes, StandardCharsets.UTF_8));
                    return CompletableFuture.completedFuture(1L);
                },
                (channelId, listener) -> subscribedConsumer.add(listener),
                (channelId, listener) -> unsubscribed.set(true),
                Runnable::run);

        List<String> received = new ArrayList<>();

        var subscription = channel.subscribe(received::add);

        assertTrue(subscription.isActive());
        assertEquals(1, subscribedConsumer.size());

        // Simulate incoming message
        subscribedConsumer.get(0).accept("Simulated Message".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("Simulated Message"), received);

        // Publish message
        long clients = channel.publishAsync("Hello").toCompletableFuture().join();
        assertEquals(1L, clients);

        // Unsubscribe
        subscription.unsubscribeAsync().toCompletableFuture().join();
        assertFalse(subscription.isActive());
        assertTrue(unsubscribed.get());
    }
}
