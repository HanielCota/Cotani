package com.cotani.redis.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.lock.LockKey;
import com.cotani.redis.lock.LockToken;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class RedisConcurrencyStressTest {
    @Test
    void concurrentPubSubDeliveryPreservesEveryDistinctMessage() {
        var incoming = new AtomicReference<Consumer<byte[]>>();
        var received = ConcurrentHashMap.<String>newKeySet();
        var channelId = ChannelId.of("stress:players");
        var channel = new DefaultRedisChannel<String>(
                channelId,
                RedisCodec.string(),
                (ignored, bytes) -> {
                    Objects.requireNonNull(incoming.get(), "subscriber").accept(bytes);
                    return CompletableFuture.completedFuture(1L);
                },
                (ignored, listener) -> incoming.set(listener),
                (ignored, listener) -> incoming.compareAndSet(listener, null),
                Runnable::run);
        var subscription = channel.subscribe(received::add);

        var subscribers = StressTestSupport.concurrent(
                "redis",
                "pub-sub-delivery",
                1_000,
                32,
                Duration.ofSeconds(20),
                index -> channel.publishAsync("player-event-" + index));

        assertEquals(1_000, received.size());
        assertEquals(1_000, subscribers.stream().filter(count -> count == 1L).count());
        subscription.unsubscribeAsync().toCompletableFuture().join();
        assertFalse(subscription.isActive());
    }

    @Test
    void concurrentDuplicateReleasesInvokeTheRemoteReleaseOnce() {
        var releases = new AtomicInteger();
        var lock = new DefaultDistributedLock(
                LockKey.of("stress:lock"), LockToken.of("owner"), Duration.ofMinutes(1), (ignoredKey, ignoredToken) -> {
                    releases.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                });

        StressTestSupport.concurrent(
                "redis",
                "duplicate-lock-release",
                1_000,
                32,
                Duration.ofSeconds(20),
                ignored -> lock.releaseAsync().thenApply(_ -> true));

        assertEquals(1, releases.get());
        assertFalse(lock.isHeld());
    }
}
