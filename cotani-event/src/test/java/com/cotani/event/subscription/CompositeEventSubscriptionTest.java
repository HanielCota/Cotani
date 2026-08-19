package com.cotani.event.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventPriority;
import org.junit.jupiter.api.Test;

final class CompositeEventSubscriptionTest {

    @Test
    void shouldStartEmpty() {
        try (var composite = new CompositeEventSubscription()) {
            assertEquals(0, composite.size());
        }
    }

    @Test
    void shouldTrackMembersAfterAddAndRemove() {
        var composite = new CompositeEventSubscription();
        EventSubscription first = subscription();
        EventSubscription second = subscription();
        composite.add(first);
        composite.add(second);
        assertEquals(2, composite.size());

        composite.remove(first);

        assertEquals(1, composite.size());
        composite.close();
    }

    @Test
    void shouldUnsubscribeAllMembersOnUnsubscribeAll() {
        var composite = new CompositeEventSubscription();
        EventSubscription first = subscription();
        EventSubscription second = subscription();
        composite.add(first);
        composite.add(second);

        composite.unsubscribeAll();

        assertEquals(0, composite.size());
        assertFalse(first.active());
        assertFalse(second.active());
    }

    @Test
    void shouldCloseIdempotently() {
        var composite = new CompositeEventSubscription();
        composite.add(subscription());
        composite.close();

        composite.close();

        assertEquals(0, composite.size());
    }

    @Test
    void shouldUnsubscribeSubscriptionAddedAfterClose() {
        var composite = new CompositeEventSubscription();
        composite.close();
        EventSubscription late = subscription();

        composite.add(late);

        assertEquals(0, composite.size());
        assertFalse(late.active());
    }

    @Test
    void shouldUnsubscribeSubscriptionAddedAfterUnsubscribeAll() {
        var composite = new CompositeEventSubscription();
        composite.unsubscribeAll();
        EventSubscription late = subscription();

        composite.add(late);

        assertEquals(0, composite.size());
        assertFalse(late.active());
    }

    @Test
    void shouldIgnoreRemovingUnknownMember() {
        var composite = new CompositeEventSubscription();
        composite.add(subscription());

        composite.remove(subscription());

        assertEquals(1, composite.size());
        composite.close();
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullOnAdd() {
        var composite = new CompositeEventSubscription();

        assertThrows(NullPointerException.class, () -> composite.add(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullOnRemove() {
        var composite = new CompositeEventSubscription();

        assertThrows(NullPointerException.class, () -> composite.remove(null));
    }

    private static EventSubscription subscription() {
        return DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {});
    }

    private record TestEvent() implements CotaniEvent {}
}
