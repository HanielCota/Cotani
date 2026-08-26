package com.cotani.metrics.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Verifies the functional {@link MeterBinder} contract.
 */
class MeterBinderTest {

    @Test
    void shouldBeUsableAsLambdaExpression() {
        AtomicInteger invocations = new AtomicInteger();
        MeterBinder binder = registry -> invocations.incrementAndGet();

        binder.bindTo(new NoOpMetricsRegistry());

        assertEquals(1, invocations.get());
    }

    @Test
    void shouldReceiveTargetRegistryOnBindTo() {
        NoOpMetricsRegistry target = new NoOpMetricsRegistry();
        AtomicReference<MetricsRegistry> received = new AtomicReference<>();
        MeterBinder binder = received::set;

        binder.bindTo(target);

        assertSame(target, received.get());
    }
}
