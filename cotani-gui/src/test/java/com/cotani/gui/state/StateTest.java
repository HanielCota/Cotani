package com.cotani.gui.state;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Extra {@link State}/{@link com.cotani.gui.api.Property} scenarios not covered by {@link PropertyTest}:
 * null validations, observer failure isolation and subscription close idempotency.
 */
final class StateTest {
    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullInitialValue() {
        assertThrows(NullPointerException.class, () -> State.of(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullSetValue() {
        var property = State.of("a");

        assertThrows(NullPointerException.class, () -> property.set(null));
        assertEquals("a", property.get());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullMutator() {
        var property = State.of("a");

        assertThrows(NullPointerException.class, () -> property.update(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectMutatorReturningNull() {
        var property = State.of("a");

        assertThrows(NullPointerException.class, () -> property.update(value -> null));
        assertEquals("a", property.get());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullObserver() {
        var property = State.of("a");

        assertThrows(NullPointerException.class, () -> property.observe(null));
    }

    @Test
    void shouldIsolateFailingObservers() {
        var property = State.of(0);
        List<Integer> observed = new ArrayList<>();

        property.observe(value -> {
            throw new IllegalStateException("observer failure");
        });
        property.observe(observed::add);

        property.set(1);

        assertEquals(List.of(1), observed);
        assertEquals(1, property.get());
    }

    @Test
    void shouldAllowClosingSubscriptionTwice() {
        var property = State.of(0);
        List<Integer> observed = new ArrayList<>();
        var subscription = property.observe(observed::add);

        subscription.close();
        assertDoesNotThrow(subscription::close);

        property.set(1);

        assertEquals(List.of(), observed);
    }
}
