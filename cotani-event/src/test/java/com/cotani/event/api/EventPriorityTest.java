package com.cotani.event.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.cotani.event.subscription.DefaultEventSubscription;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EventPriorityTest {

    @Test
    void shouldDeclarePrioritiesInAscendingExecutionOrder() {
        assertEquals(
                List.of(
                        EventPriority.LOWEST,
                        EventPriority.LOW,
                        EventPriority.NORMAL,
                        EventPriority.HIGH,
                        EventPriority.HIGHEST,
                        EventPriority.MONITOR),
                List.of(EventPriority.values()));
    }

    @Test
    void shouldUseNormalAsDefaultPriorityForSubscriptions() {
        DefaultEventSubscription subscription =
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, _ -> {});

        assertSame(EventPriority.NORMAL, subscription.priority());
    }

    private record TestEvent() implements CotaniEvent {}
}
