package net.cotani.metrics;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.cotani.metrics.api.NoOpMetricsRegistry;
import net.cotani.metrics.config.MetricsConfig;
import net.cotani.metrics.exporter.PrometheusServer;
import org.junit.jupiter.api.Test;

/**
 * Verifies the lifecycle and validation behavior of {@link CotaniMetricsModule}.
 */
class CotaniMetricsModuleLifecycleTest {

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullConfig() {
        assertThrows(NullPointerException.class, () -> CotaniMetricsModule.create(null));
    }

    @Test
    void shouldExposeBoundConfig() {
        MetricsConfig config = new MetricsConfig(false, "cotani", 9090, "/metrics");

        try (CotaniMetricsModule module = CotaniMetricsModule.create(config)) {
            assertSame(config, module.config());
        }
    }

    @Test
    void shouldUseNoOpRegistryWhenDisabled() {
        MetricsConfig config = new MetricsConfig(false, "cotani", 9090, "/metrics");

        try (CotaniMetricsModule module = CotaniMetricsModule.create(config)) {
            assertFalse(module.isEnabled());
            assertInstanceOf(NoOpMetricsRegistry.class, module.registry());
            assertTrue(module.prometheusServer().isEmpty());
        }
    }

    @Test
    void shouldUseConfiguredHostAndPathForServer() {
        MetricsConfig config = new MetricsConfig(true, "cotani", 0, "/scrape");

        try (CotaniMetricsModule module = CotaniMetricsModule.create(config)) {
            PrometheusServer server = module.prometheusServer().orElseThrow();
            assertTrue(server.isRunning());
            assertEquals(MetricsConfig.DEFAULT_HOST, server.host());
            assertEquals("/scrape", server.path());
            assertTrue(server.port() >= 0);
        }
    }

    @Test
    void shouldStopPrometheusServerOnClose() {
        MetricsConfig config = new MetricsConfig(true, "cotani", 0, "/metrics");
        CotaniMetricsModule module = CotaniMetricsModule.create(config);
        PrometheusServer server = module.prometheusServer().orElseThrow();

        module.close();

        assertFalse(server.isRunning());
    }

    @Test
    void shouldCloseIdempotently() {
        MetricsConfig config = new MetricsConfig(true, "cotani", 0, "/metrics");
        CotaniMetricsModule module = CotaniMetricsModule.create(config);

        assertDoesNotThrow(module::close);
        assertDoesNotThrow(module::close);
    }

    @Test
    void shouldFailCleanlyWhenPortIsAlreadyInUse() throws IOException {
        HttpServer blocker = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        blocker.start();
        try {
            MetricsConfig config =
                    new MetricsConfig(true, "cotani", blocker.getAddress().getPort(), "/metrics");

            assertThrows(IllegalStateException.class, () -> CotaniMetricsModule.create(config));
        } finally {
            blocker.stop(0);
        }
    }
}
