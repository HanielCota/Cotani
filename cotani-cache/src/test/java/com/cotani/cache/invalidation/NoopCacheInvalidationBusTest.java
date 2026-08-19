package com.cotani.cache.invalidation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class NoopCacheInvalidationBusTest {
    private final NoopCacheInvalidationBus<String> bus = new NoopCacheInvalidationBus<>();

    @Test
    void subscribeReturnsNoopSubscription() {
        var subscription = bus.subscribe(ignored -> fail("listener must not be invoked"));

        assertDoesNotThrow(subscription::close);
        assertDoesNotThrow(subscription::close);
    }

    @Test
    void publishCompletesWithoutInvokingListeners() {
        var invoked = new AtomicInteger();
        bus.subscribe(ignored -> invoked.incrementAndGet());

        bus.publish(new CacheInvalidation<>(UUID.randomUUID(), "key"))
                .toCompletableFuture()
                .join();

        assertEquals(0, invoked.get());
    }

    @Test
    void publishReturnsCompletedStage() {
        var stage = bus.publish(new CacheInvalidation<>(UUID.randomUUID(), "key"));

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
