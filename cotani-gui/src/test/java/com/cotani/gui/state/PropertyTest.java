package com.cotani.gui.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PropertyTest {
    @Test
    void getsAndSetsValues() {
        var property = State.of("a");

        assertEquals("a", property.get());
        property.set("b");
        assertEquals("b", property.get());
    }

    @Test
    void updatesWithMutator() {
        var property = State.of(1);

        property.update(value -> value + 1);

        assertEquals(2, property.get());
    }

    @Test
    void togglesBooleanProperty() {
        var property = State.of(false);

        property.toggle();
        assertTrue(property.get());
        property.toggle();
        assertFalse(property.get());
    }

    @Test
    void notifiesObserversOnChange() {
        var property = State.of(0);
        List<Integer> observed = new ArrayList<>();
        var _ = property.observe(observed::add);

        property.set(1);
        property.update(value -> value + 1);

        assertEquals(List.of(1, 2), observed);
    }

    @Test
    void doesNotNotifyWhenValueIsUnchanged() {
        var property = State.of(10);
        List<Integer> observed = new ArrayList<>();
        var _ = property.observe(observed::add);

        property.set(10);
        property.update(value -> value);

        assertTrue(observed.isEmpty());
    }

    @Test
    void stopsNotifyingAfterSubscriptionCloses() {
        var property = State.of(0);
        List<Integer> observed = new ArrayList<>();
        var subscription = property.observe(observed::add);

        property.set(1);
        subscription.close();
        property.set(2);

        assertEquals(List.of(1), observed);
    }
}
