package com.cotani.event.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventPriority;
import com.cotani.event.subscription.DefaultEventSubscription;
import com.cotani.event.subscription.EventSubscription;
import org.junit.jupiter.api.Test;

final class EventListenerExceptionTest {

    private static final IllegalStateException CAUSE = new IllegalStateException("boom");

    @Test
    void shouldExposeEventSubscriptionAndCause() {
        TestEvent event = new TestEvent();
        EventSubscription subscription = subscription();
        EventListenerException exception = new EventListenerException(event, subscription, CAUSE);

        assertSame(event, exception.event());
        assertSame(subscription, exception.subscription());
        assertSame(CAUSE, exception.getCause());
        assertEquals("Failed to dispatch event TestEvent", exception.getMessage());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldAllowNullCause() {
        EventListenerException exception = new EventListenerException(new TestEvent(), subscription(), null);

        assertNull(exception.getCause());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullEvent() {
        assertThrows(NullPointerException.class, () -> new EventListenerException(null, subscription(), CAUSE));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullSubscription() {
        assertThrows(NullPointerException.class, () -> new EventListenerException(new TestEvent(), null, CAUSE));
    }

    private static EventSubscription subscription() {
        return DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {});
    }

    private record TestEvent() implements CotaniEvent {}
}
