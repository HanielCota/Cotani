package com.cotani.event.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DefaultEventSubscriptionTest {

    @Test
    void shouldStartActive() {
        assertTrue(subscription().active());
    }

    @Test
    void shouldBecomeInactiveAfterUnsubscribe() {
        EventSubscription subscription = subscription();

        subscription.unsubscribe();

        assertFalse(subscription.active());
    }

    @Test
    void shouldRemainInactiveAfterRepeatedUnsubscribe() {
        EventSubscription subscription = subscription();
        subscription.unsubscribe();

        subscription.unsubscribe();

        assertFalse(subscription.active());
    }

    @Test
    void shouldExposeRegisteredProperties() {
        UUID id = UUID.randomUUID();
        EventListener<TestEvent> listener = event -> {};
        EventSubscription subscription =
                new DefaultEventSubscription(id, TestEvent.class, EventPriority.HIGH, true, listener);

        assertEquals(id, subscription.id());
        assertEquals(TestEvent.class, subscription.eventType());
        assertEquals(EventPriority.HIGH, subscription.priority());
        assertTrue(subscription.ignoreCancelled());
        assertSame(listener, subscription.listener());
        assertTrue(subscription.active());
    }

    @Test
    void shouldDefaultToNotIgnoreCancelledEvents() {
        assertFalse(subscription().ignoreCancelled());
    }

    @Test
    void shouldGenerateUniqueIdsPerCreate() {
        assertNotEquals(
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {})
                        .id(),
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {})
                        .id());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullConstructorArguments() {
        EventListener<TestEvent> listener = event -> {};
        assertThrows(
                NullPointerException.class,
                () -> new DefaultEventSubscription(null, TestEvent.class, EventPriority.NORMAL, listener));
        assertThrows(
                NullPointerException.class,
                () -> new DefaultEventSubscription(UUID.randomUUID(), null, EventPriority.NORMAL, listener));
        assertThrows(
                NullPointerException.class,
                () -> new DefaultEventSubscription(UUID.randomUUID(), TestEvent.class, null, listener));
        assertThrows(
                NullPointerException.class,
                () -> new DefaultEventSubscription(UUID.randomUUID(), TestEvent.class, EventPriority.NORMAL, null));
    }

    private static EventSubscription subscription() {
        return DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {});
    }

    private record TestEvent() implements CotaniEvent {}
}
