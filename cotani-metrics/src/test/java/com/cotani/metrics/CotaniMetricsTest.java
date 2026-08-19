package com.cotani.metrics;

import static org.junit.jupiter.api.Assertions.*;

import net.cotani.metrics.CotaniMetricsRegistry;
import net.cotani.metrics.api.NoOpMetricsRegistry;
import net.cotani.metrics.config.MetricsConfig;
import org.junit.jupiter.api.Test;

/**
 * Verifies the stable {@code com.cotani} factory entry point for the metrics module.
 */
class CotaniMetricsTest {

    @Test
    void shouldCreateDisabledModuleWithNoOpRegistry() {
        MetricsConfig config = new MetricsConfig(false, "cotani", 9090, "/metrics");

        try (var module = CotaniMetrics.create(config)) {
            assertFalse(module.isEnabled());
            assertInstanceOf(NoOpMetricsRegistry.class, module.registry());
            assertTrue(module.prometheusServer().isEmpty());
        }
    }

    @Test
    void shouldCreateEnabledModuleWithActiveRegistry() {
        MetricsConfig config = new MetricsConfig(true, "app", 0, "/metrics");

        try (var module = CotaniMetrics.create(config)) {
            assertTrue(module.isEnabled());
            assertInstanceOf(CotaniMetricsRegistry.class, module.registry());
            assertTrue(module.prometheusServer().isPresent());
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullConfig() {
        assertThrows(NullPointerException.class, () -> CotaniMetrics.create(null));
    }
}
