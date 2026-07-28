package net.cotani.metrics;

import static org.junit.jupiter.api.Assertions.*;

import net.cotani.metrics.api.NoOpMetricsRegistry;
import net.cotani.metrics.config.MetricsConfig;
import org.junit.jupiter.api.Test;

class CotaniMetricsModuleTest {

    @Test
    void disabledModuleUsesNoOpRegistry() {
        MetricsConfig config = new MetricsConfig(false, "cotani", 9090, "/metrics");
        try (CotaniMetricsModule module = CotaniMetricsModule.create(config)) {
            assertFalse(module.isEnabled());
            assertInstanceOf(NoOpMetricsRegistry.class, module.registry());
            assertNull(module.prometheusServer());
        }
    }

    @Test
    void enabledModuleCreatesActiveRegistryAndServer() {
        MetricsConfig config = new MetricsConfig(true, "test_prefix", 0, "/metrics");
        try (CotaniMetricsModule module = CotaniMetricsModule.create(config)) {
            assertTrue(module.isEnabled());
            assertInstanceOf(CotaniMetricsRegistry.class, module.registry());
            assertNotNull(module.prometheusServer());
            assertTrue(module.prometheusServer().isRunning());
        }
    }
}
