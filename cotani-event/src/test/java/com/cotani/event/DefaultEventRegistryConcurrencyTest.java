package com.cotani.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import com.cotani.event.registry.DefaultEventRegistry;
import com.cotani.event.subscription.DefaultEventSubscription;
import com.cotani.event.subscription.EventSubscription;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultEventRegistryConcurrencyTest {

    @Test
    void registrationCannotBeLostBehindConcurrentResolution() throws Exception {
        var registry = new DefaultEventRegistry();
        var resolutionEntered = new CountDownLatch(1);
        var releaseResolution = new CountDownLatch(1);
        registry.register(new BlockingSubscription(resolutionEntered, releaseResolution));
        var added = DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {});
        var executor = Executors.newFixedThreadPool(2);

        try {
            var resolving = executor.submit(() -> registry.subscriptionsFor(new TestEvent()));
            assertTrue(resolutionEntered.await(5, TimeUnit.SECONDS));

            var registering = executor.submit(() -> registry.register(added));
            assertFalse(registering.isDone(), "registration must wait for the in-flight snapshot");

            releaseResolution.countDown();
            assertEquals(1, resolving.get(5, TimeUnit.SECONDS).size());
            registering.get(5, TimeUnit.SECONDS);

            assertEquals(2, registry.subscriptionsFor(new TestEvent()).size());
        } finally {
            releaseResolution.countDown();
            executor.shutdownNow();
        }
    }

    private record TestEvent() implements CotaniEvent {}

    private static final class BlockingSubscription implements EventSubscription {

        private final UUID id = UUID.randomUUID();
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private BlockingSubscription(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public Class<? extends CotaniEvent> eventType() {
            return TestEvent.class;
        }

        @Override
        public EventPriority priority() {
            return EventPriority.NORMAL;
        }

        @Override
        public EventListener<? extends CotaniEvent> listener() {
            return _ -> {};
        }

        @Override
        public boolean active() {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to release resolution");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
            return active.get();
        }

        @Override
        public void unsubscribe() {
            active.set(false);
        }
    }
}
