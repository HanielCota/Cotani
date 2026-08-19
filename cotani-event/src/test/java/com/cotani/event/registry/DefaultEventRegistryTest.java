package com.cotani.event.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventPriority;
import com.cotani.event.subscription.DefaultEventSubscription;
import com.cotani.event.subscription.EventSubscription;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultEventRegistryTest {

    @Test
    void shouldReturnEmptySnapshotForUnknownEventType() {
        var registry = new DefaultEventRegistry();

        assertTrue(registry.subscriptionsFor(new TestEvent()).isEmpty());
    }

    @Test
    void shouldReturnSubscriptionsSortedByPriority() {
        var registry = new DefaultEventRegistry();
        EventSubscription lowest = subscription(EventPriority.LOWEST);
        EventSubscription highest = subscription(EventPriority.HIGHEST);
        EventSubscription normal = subscription(EventPriority.NORMAL);
        registry.register(highest);
        registry.register(lowest);
        registry.register(normal);

        List<EventSubscription> snapshot = registry.subscriptionsFor(new TestEvent());

        assertEquals(List.of(lowest, normal, highest), snapshot);
    }

    @Test
    void shouldKeepRegistrationOrderWithinSamePriority() {
        var registry = new DefaultEventRegistry();
        EventSubscription first = subscription(EventPriority.NORMAL);
        EventSubscription second = subscription(EventPriority.NORMAL);
        registry.register(first);
        registry.register(second);

        assertEquals(List.of(first, second), registry.subscriptionsFor(new TestEvent()));
    }

    @Test
    void shouldMatchSubtypeEventsToSupertypeSubscriptions() {
        var registry = new DefaultEventRegistry();
        registry.register(subscription(BaseEvent.class, EventPriority.NORMAL));

        List<EventSubscription> snapshot = registry.subscriptionsFor(new ChildEvent());

        assertEquals(1, snapshot.size());
    }

    @Test
    void shouldExcludeUnrelatedEventTypes() {
        var registry = new DefaultEventRegistry();
        registry.register(subscription(TestEvent.class, EventPriority.NORMAL));

        assertTrue(registry.subscriptionsFor(new OtherEvent()).isEmpty());
    }

    @Test
    void shouldExcludeInactiveSubscriptions() {
        var registry = new DefaultEventRegistry();
        EventSubscription stale = subscription(EventPriority.NORMAL);
        registry.register(stale);
        stale.unsubscribe();

        assertTrue(registry.subscriptionsFor(new TestEvent()).isEmpty());
    }

    @Test
    void shouldUnsubscribeIdempotently() {
        var registry = new DefaultEventRegistry();
        EventSubscription subscription = subscription(EventPriority.NORMAL);
        registry.register(subscription);

        registry.unregister(subscription);
        registry.unregister(subscription);
        registry.unregister(subscription(EventPriority.NORMAL));

        assertFalse(subscription.active());
        assertTrue(registry.subscriptionsFor(new TestEvent()).isEmpty());
    }

    @Test
    void shouldReturnImmutableSnapshot() {
        var registry = new DefaultEventRegistry();
        registry.register(subscription(EventPriority.NORMAL));

        List<EventSubscription> snapshot = registry.subscriptionsFor(new TestEvent());

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(subscription(EventPriority.HIGH)));
    }

    @Test
    void shouldCacheSnapshotUntilSubscriptionsChange() {
        var registry = new DefaultEventRegistry();
        registry.register(subscription(EventPriority.NORMAL));
        List<EventSubscription> first = registry.subscriptionsFor(new TestEvent());

        List<EventSubscription> second = registry.subscriptionsFor(new TestEvent());
        assertSame(first, second);

        registry.register(subscription(EventPriority.HIGH));
        List<EventSubscription> third = registry.subscriptionsFor(new TestEvent());
        assertNotSame(first, third);
        assertEquals(2, third.size());
    }

    @Test
    void shouldClearAllSubscriptions() {
        var registry = new DefaultEventRegistry();
        EventSubscription first = subscription(EventPriority.NORMAL);
        EventSubscription second = subscription(EventPriority.LOWEST);
        registry.register(first);
        registry.register(second);

        registry.clear();

        assertTrue(registry.subscriptionsFor(new TestEvent()).isEmpty());
        assertFalse(first.active());
        assertFalse(second.active());
    }

    @Test
    void shouldRemoveInactiveSubscriptions() {
        var registry = new DefaultEventRegistry();
        EventSubscription stale = subscription(EventPriority.NORMAL);
        registry.register(stale);
        stale.unsubscribe();
        registry.register(subscription(EventPriority.NORMAL));

        registry.removeInactive();

        assertEquals(1, registry.subscriptionsFor(new TestEvent()).size());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        var registry = new DefaultEventRegistry();
        assertThrows(NullPointerException.class, () -> registry.register(null));
        assertThrows(NullPointerException.class, () -> registry.unregister(null));
        assertThrows(NullPointerException.class, () -> registry.subscriptionsFor(null));
    }

    private static EventSubscription subscription(EventPriority priority) {
        return subscription(TestEvent.class, priority);
    }

    private static EventSubscription subscription(Class<? extends CotaniEvent> eventType, EventPriority priority) {
        return DefaultEventSubscription.create(eventType, priority, _ -> {});
    }

    private record TestEvent() implements CotaniEvent {}

    private record OtherEvent() implements CotaniEvent {}

    private static class BaseEvent implements CotaniEvent {}

    private static final class ChildEvent extends BaseEvent {}
}
