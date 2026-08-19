package com.cotani.event.cancellable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AbstractCancellableEventTest {

    @Test
    void shouldStartUncancelled() {
        assertFalse(new TestCancellableEvent().cancelled());
    }

    @Test
    void shouldBeCancelledAfterCancel() {
        var event = new TestCancellableEvent();

        event.cancel();

        assertTrue(event.cancelled());
    }

    @Test
    void shouldRemainCancelledAfterRepeatedCancel() {
        var event = new TestCancellableEvent();

        event.cancel();
        event.cancel();

        assertTrue(event.cancelled());
    }

    @Test
    void shouldBeUncancelledAfterUncancel() {
        var event = new TestCancellableEvent();
        event.cancel();

        event.uncancel();

        assertFalse(event.cancelled());
    }

    @Test
    void shouldBeCancelledAgainAfterUncancelAndCancel() {
        var event = new TestCancellableEvent();
        event.cancel();
        event.uncancel();

        event.cancel();

        assertTrue(event.cancelled());
    }

    private static final class TestCancellableEvent extends AbstractCancellableEvent {}
}
