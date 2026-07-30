package net.cotani.metrics;

import java.util.Objects;
import java.util.Optional;
import net.cotani.metrics.api.MetricsRegistry;
import net.cotani.metrics.api.NoOpMetricsRegistry;
import net.cotani.metrics.config.MetricsConfig;
import net.cotani.metrics.exporter.PrometheusServer;
import org.jspecify.annotations.Nullable;

/**
 * Main lifecycle and bootstrap module for Cotani metrics collection.
 */
public final class CotaniMetricsModule implements AutoCloseable {

    private final MetricsConfig config;
    private final MetricsRegistry registry;
    private final @Nullable PrometheusServer prometheusServer;

    private CotaniMetricsModule(
            MetricsConfig config, MetricsRegistry registry, @Nullable PrometheusServer prometheusServer) {
        this.config = Objects.requireNonNull(config, "config");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.prometheusServer = prometheusServer;
    }

    /**
     * Creates and initializes a new {@code CotaniMetricsModule} based on configuration.
     *
     * @param config configuration parameters
     * @return initialized module instance
     */
    public static CotaniMetricsModule create(MetricsConfig config) {
        Objects.requireNonNull(config, "config");
        if (!config.enabled()) {
            return new CotaniMetricsModule(config, NoOpMetricsRegistry.INSTANCE, null);
        }

        CotaniMetricsRegistry cotaniRegistry = new CotaniMetricsRegistry(config.prefix());
        PrometheusServer server = null;

        var prometheus = cotaniRegistry.prometheusRegistry();
        if (prometheus.isPresent()) {
            PrometheusServer pServer =
                    new PrometheusServer(prometheus.get(), config.host(), config.port(), config.path());
            try {
                pServer.start();
                server = pServer;
            } catch (Throwable t) {
                pServer.close();
                cotaniRegistry.close();
                throw t;
            }
        }

        return new CotaniMetricsModule(config, cotaniRegistry, server);
    }

    public MetricsConfig config() {
        return config;
    }

    public MetricsRegistry registry() {
        return registry;
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    public Optional<PrometheusServer> prometheusServer() {
        return Optional.ofNullable(prometheusServer);
    }

    @Override
    public void close() {
        if (prometheusServer != null) {
            prometheusServer.close();
        }
        registry.close();
    }
}
