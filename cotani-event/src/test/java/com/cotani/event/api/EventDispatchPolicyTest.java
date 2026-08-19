package com.cotani.event.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class EventDispatchPolicyTest {

    @Test
    void shouldProvideDefaultTimeoutAndUnsubscribeOnTimeout() {
        EventDispatchPolicy policy = EventDispatchPolicy.defaults();

        assertEquals(Duration.ofSeconds(5), policy.listenerTimeout());
        assertTrue(policy.unsubscribeOnTimeout());
    }

    @Test
    void shouldPreserveConfiguredValues() {
        EventDispatchPolicy policy = new EventDispatchPolicy(Duration.ofMillis(250), false);

        assertEquals(Duration.ofMillis(250), policy.listenerTimeout());
        assertFalse(policy.unsubscribeOnTimeout());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullListenerTimeout() {
        assertThrows(NullPointerException.class, () -> new EventDispatchPolicy(null, true));
    }

    @Test
    void shouldRejectZeroListenerTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new EventDispatchPolicy(Duration.ZERO, true));
    }

    @Test
    void shouldRejectNegativeListenerTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new EventDispatchPolicy(Duration.ofSeconds(-1), true));
    }
}
