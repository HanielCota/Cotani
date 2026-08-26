package com.cotani.metrics.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Contract tests shared by every {@link MetricsRegistry} implementation.
 *
 * <p>Every method of the interface must accept valid inputs without throwing, {@code register}
 * must invoke {@link MeterBinder#bindTo} exactly once with the target registry, and {@code close}
 * must be safe to call.
 */
public abstract class MetricsRegistryContractTest {

    protected abstract MetricsRegistry newRegistry();

    protected abstract boolean expectEnabled();

    protected abstract boolean expectMeterRegistryPresent();

    @Test
    void shouldReportConfiguredEnabledState() {
        assertEquals(expectEnabled(), newRegistry().isEnabled());
    }

    @Test
    void shouldAcceptCounterIncrementWithoutTags() {
        assertDoesNotThrow(() -> newRegistry().counter("requests"));
    }

    @Test
    void shouldAcceptCounterIncrementWithTags() {
        assertDoesNotThrow(() -> newRegistry().counter("requests", "region", "us", "status", "ok"));
    }

    @Test
    void shouldAcceptCounterAmount() {
        assertDoesNotThrow(() -> newRegistry().counter("requests", 2.5, "region", "us"));
    }

    @Test
    void shouldAcceptGaugeRegistration() {
        assertDoesNotThrow(() -> newRegistry().gauge("queue_size", () -> 3, "queue", "main"));
    }

    @Test
    void shouldAcceptTimerRecording() {
        assertDoesNotThrow(() -> newRegistry().timer("latency", Duration.ofMillis(12), "op", "read"));
    }

    @Test
    void shouldAcceptBinderOnRegister() {
        MetricsRegistry registry = newRegistry();

        assertDoesNotThrow(() -> registry.register(ignored -> {}));
    }

    @Test
    void shouldExposeMeterRegistryOptional() {
        assertEquals(expectMeterRegistryPresent(), newRegistry().meterRegistry().isPresent());
    }

    @Test
    void shouldCloseWithoutThrowing() {
        assertDoesNotThrow(newRegistry()::close);
    }

    @Test
    void shouldSupportMultipleCloseCalls() {
        MetricsRegistry registry = newRegistry();

        assertDoesNotThrow(registry::close);
        assertDoesNotThrow(registry::close);
    }
}
