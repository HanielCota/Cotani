package com.cotani.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventPriority;
import com.cotani.event.bus.DefaultEventBus;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class EventBusStressTest {
    @Test
    void thousandsOfEventsRespectListenerLifecyclePriorityAndFailureIsolation() {
        var listenerFailures = new AtomicInteger();
        var deliveries = new AtomicInteger();
        var bus = DefaultEventBus.create(_ -> listenerFailures.incrementAndGet(), Runnable::run);
        var subscriptions = new ArrayList<com.cotani.event.subscription.EventSubscription>();
        try {
            for (int index = 0; index < 100; index++) {
                int listenerIndex = index;
                var priority =
                        switch (index % 6) {
                            case 0 -> EventPriority.LOWEST;
                            case 1 -> EventPriority.LOW;
                            case 2 -> EventPriority.NORMAL;
                            case 3 -> EventPriority.HIGH;
                            case 4 -> EventPriority.HIGHEST;
                            default -> EventPriority.MONITOR;
                        };
                subscriptions.add(bus.subscribe(StressEvent.class, priority, event -> {
                    if (listenerIndex == 0 && event.sequence() % 100 == 0) {
                        throw new IllegalStateException("generated listener failure " + event.sequence());
                    }
                    deliveries.incrementAndGet();
                }));
            }

            for (int sequence = 0; sequence < 1_200; sequence++) {
                bus.publish(new StressEvent(sequence));
            }
            assertEquals(12, listenerFailures.get());
            assertEquals(120_000 - 12, deliveries.get());

            subscriptions.subList(0, 50).forEach(bus::unsubscribe);
            int before = deliveries.get();
            for (int sequence = 0; sequence < 1_000; sequence++) {
                bus.publish(new StressEvent(10_000 + sequence));
            }
            assertEquals(before + 50_000, deliveries.get());
            assertFalse(subscriptions.getFirst().active());
        } finally {
            bus.close();
        }
    }

    private record StressEvent(int sequence) implements CotaniEvent {}
}
