package com.cotani.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventPriority;
import com.cotani.event.bus.DefaultEventBus;
import com.cotani.event.cancellable.AbstractCancellableEvent;
import com.cotani.event.exception.LoggingEventExceptionHandler;
import com.cotani.event.subscription.CompositeEventSubscription;
import com.cotani.event.subscription.EventSubscription;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class EventBusTest {

    @Test
    void testPublishAndSubscribe() {
        EventBus eventBus = DefaultEventBus.create(LoggingEventExceptionHandler.usingJavaLogger(), Runnable::run);
        AtomicBoolean called = new AtomicBoolean(false);
        UUID userId = UUID.randomUUID();

        eventBus.subscribe(TestUserEvent.class, event -> {
            assertEquals(userId, event.userId());
            called.set(true);
        });

        eventBus.publish(new TestUserEvent(userId));
        assertTrue(called.get(), "Listener should have been called");
    }

    @Test
    void testPublishAsync() {
        EventBus eventBus = DefaultEventBus.create(LoggingEventExceptionHandler.usingJavaLogger(), Runnable::run);
        AtomicBoolean called = new AtomicBoolean(false);
        UUID userId = UUID.randomUUID();

        eventBus.subscribe(TestUserEvent.class, event -> {
            assertEquals(userId, event.userId());
            called.set(true);
        });

        eventBus.publishAsync(new TestUserEvent(userId)).toCompletableFuture().join();

        assertTrue(called.get(), "Async listener should have been called");
    }

    @Test
    void testIgnoreCancelled() {
        EventBus eventBus = DefaultEventBus.create(LoggingEventExceptionHandler.usingJavaLogger(), Runnable::run);
        AtomicBoolean defaultCalled = new AtomicBoolean(false);
        AtomicBoolean ignoreCalled = new AtomicBoolean(false);

        // 1. Subscribe with ignoreCancelled = false (default)
        eventBus.subscribe(TestCancellableEvent.class, event -> {
            defaultCalled.set(true);
        });

        // 2. Subscribe with ignoreCancelled = true
        eventBus.subscribe(TestCancellableEvent.class, EventPriority.NORMAL, true, event -> {
            ignoreCalled.set(true);
        });

        // Publish cancelled event
        TestCancellableEvent event = new TestCancellableEvent();
        event.cancel();

        eventBus.publish(event);

        assertTrue(
                defaultCalled.get(),
                "Default listener (ignoreCancelled=false) should be called even if event is cancelled");
        assertFalse(
                ignoreCalled.get(), "Listener with ignoreCancelled=true should NOT be called when event is cancelled");
    }

    @Test
    void testCompositeEventSubscription() {
        EventBus eventBus = DefaultEventBus.create(LoggingEventExceptionHandler.usingJavaLogger(), Runnable::run);
        AtomicInteger counter = new AtomicInteger(0);

        try (CompositeEventSubscription composite = new CompositeEventSubscription()) {
            EventSubscription sub1 = eventBus.subscribe(TestUserEvent.class, event -> counter.incrementAndGet());
            EventSubscription sub2 = eventBus.subscribe(TestUserEvent.class, event -> counter.incrementAndGet());

            composite.add(sub1);
            composite.add(sub2);
            assertEquals(2, composite.size());

            eventBus.publish(new TestUserEvent(UUID.randomUUID()));
            assertEquals(2, counter.get());

            // Unsubscribe all
            composite.unsubscribeAll();
            assertEquals(0, composite.size());

            // Publish again - counter should not increment
            eventBus.publish(new TestUserEvent(UUID.randomUUID()));
            assertEquals(2, counter.get());
        }
    }

    @Test
    void testRegistryCachingAndInvalidation() {
        EventBus eventBus = DefaultEventBus.create(LoggingEventExceptionHandler.usingJavaLogger(), Runnable::run);
        AtomicInteger counter = new AtomicInteger(0);

        // 1. First publish - cache should be filled
        eventBus.publish(new TestUserEvent(UUID.randomUUID()));
        assertEquals(0, counter.get());

        // 2. Register dynamic subscription - should invalidate cache
        EventSubscription subscription = eventBus.subscribe(TestUserEvent.class, event -> {
            counter.incrementAndGet();
        });

        // 3. Second publish - should hit new subscription
        eventBus.publish(new TestUserEvent(UUID.randomUUID()));
        assertEquals(1, counter.get());

        // 4. Unsubscribe subscription - should invalidate cache
        subscription.unsubscribe();

        // 5. Third publish - subscription shouldn't run
        eventBus.publish(new TestUserEvent(UUID.randomUUID()));
        assertEquals(1, counter.get());
    }

    private static record TestUserEvent(UUID userId) implements CotaniEvent {}

    private static final class TestCancellableEvent extends AbstractCancellableEvent {}
}
