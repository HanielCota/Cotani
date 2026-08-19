package net.cotani.metrics.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Verifies the no-op behavior of {@link NoOpMetricsRegistry}: it never throws for metric
 * operations, retains no state and returns neutral values.
 */
class NoOpMetricsRegistryTest {

    @Test
    void shouldReportDisabled() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        assertFalse(registry.isEnabled());
    }

    @Test
    void shouldReturnEmptyMeterRegistry() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        assertTrue(registry.meterRegistry().isEmpty());
    }

    @Test
    void shouldAcceptAnyCounterCallWithoutThrowing() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        assertDoesNotThrow(() -> registry.counter("requests"));
        assertDoesNotThrow(() -> registry.counter("requests", "region", "us"));
        assertDoesNotThrow(() -> registry.counter("requests", 5.0));
        assertDoesNotThrow(() -> registry.counter("requests", -5.0, "region", "us"));
        assertDoesNotThrow(() -> registry.counter("requests", 0.0, "odd"));
    }

    @Test
    void shouldAcceptGaugeAndTimerCallsWithoutThrowing() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        assertDoesNotThrow(() -> registry.gauge("queue", () -> 3, "queue", "main"));
        assertDoesNotThrow(() -> registry.timer("latency", Duration.ofMillis(12), "op", "read"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldAcceptNullMetricArgumentsWithoutThrowing() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        assertDoesNotThrow(() -> registry.counter(null));
        assertDoesNotThrow(() -> registry.counter("requests", (String[]) null));
        assertDoesNotThrow(() -> registry.gauge(null, () -> 1));
        assertDoesNotThrow(() -> registry.gauge("queue", null));
        assertDoesNotThrow(() -> registry.timer(null, null));
    }

    @Test
    void shouldNotRetainAnyState() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        registry.counter("requests", 10.0);
        registry.gauge("queue", () -> 3);
        registry.timer("latency", Duration.ofSeconds(1));

        assertTrue(registry.meterRegistry().isEmpty());
        assertFalse(registry.isEnabled());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullBinderOnRegister() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void shouldBeSafeToCloseMultipleTimes() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        assertDoesNotThrow(registry::close);
        assertDoesNotThrow(registry::close);
        assertFalse(registry.isEnabled());
    }

    @Test
    void shouldRemainUsableAfterClose() {
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();
        registry.close();

        assertDoesNotThrow(() -> registry.counter("requests"));
        assertTrue(registry.meterRegistry().isEmpty());
    }

    @Test
    void shouldNotInvokeBinderOnRegister() {
        AtomicInteger invocations = new AtomicInteger();
        NoOpMetricsRegistry registry = new NoOpMetricsRegistry();

        registry.register(binder -> invocations.incrementAndGet());

        assertEquals(0, invocations.get());
        assertTrue(registry.meterRegistry().isEmpty());
    }

    @Test
    void shouldExposeUsableSingletonInstance() {
        AtomicInteger invocations = new AtomicInteger();

        assertDoesNotThrow(() -> NoOpMetricsRegistry.INSTANCE.counter("requests"));
        assertDoesNotThrow(() -> NoOpMetricsRegistry.INSTANCE.register(binder -> invocations.incrementAndGet()));
        assertEquals(0, invocations.get());
    }
}
