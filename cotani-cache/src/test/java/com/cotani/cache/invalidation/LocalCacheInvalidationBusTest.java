package com.cotani.cache.invalidation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class LocalCacheInvalidationBusTest {
    private final LocalCacheInvalidationBus<String> bus = new LocalCacheInvalidationBus<>();

    private static CacheInvalidation<String> invalidation(String key) {
        return new CacheInvalidation<>(UUID.randomUUID(), key);
    }

    @Test
    void subscribeAndPublishDeliversToListener() {
        var received = new ArrayList<CacheInvalidation<String>>();
        bus.subscribe(received::add);

        var event = invalidation("key");
        bus.publish(event).toCompletableFuture().join();

        assertEquals(List.of(event), received);
    }

    @Test
    void publishReachesMultipleListeners() {
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        bus.subscribe(ignored -> first.incrementAndGet());
        bus.subscribe(ignored -> second.incrementAndGet());

        bus.publish(invalidation("key")).toCompletableFuture().join();

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void closingSubscriptionStopsDelivery() {
        var received = new AtomicInteger();
        var subscription = bus.subscribe(ignored -> received.incrementAndGet());

        bus.publish(invalidation("a")).toCompletableFuture().join();
        subscription.close();
        bus.publish(invalidation("b")).toCompletableFuture().join();

        assertEquals(1, received.get());
    }

    @Test
    void closingSubscriptionIsIdempotent() {
        var received = new AtomicInteger();
        var subscription = bus.subscribe(ignored -> received.incrementAndGet());

        subscription.close();
        subscription.close();

        bus.publish(invalidation("key")).toCompletableFuture().join();

        assertEquals(0, received.get());
    }

    @Test
    void publishDeliversOnlyToActiveSubscriptions() {
        var active = new AtomicInteger();
        var closed = new AtomicInteger();
        bus.subscribe(ignored -> active.incrementAndGet());
        var subscription = bus.subscribe(ignored -> closed.incrementAndGet());

        subscription.close();
        bus.publish(invalidation("key")).toCompletableFuture().join();

        assertEquals(1, active.get());
        assertEquals(0, closed.get());
    }

    @Test
    void publishReturnsCompletedStage() {
        var stage = bus.publish(invalidation("key"));

        assertTrue(stage.toCompletableFuture().isDone());
    }

    @Test
    void subscribeNullRejects() {
        assertThrows(NullPointerException.class, () -> bus.subscribe(null));
    }

    @Test
    void publishNullRejects() {
        assertThrows(NullPointerException.class, () -> bus.publish(null));
    }
}
