package net.cotani.metrics.config;

import com.cotani.config.annotation.Default;
import com.cotani.config.annotation.Range;

/**
 * Immutable configuration settings for Cotani metrics collection and export.
 *
 * @param enabled whether metrics collection is enabled
 * @param prefix  prefix for metric names
 * @param port    HTTP port for Prometheus scraping endpoint
 * @param path    path for Prometheus scraping endpoint
 */
public record MetricsConfig(
        @Default("false") boolean enabled,
        @Default("cotani") String prefix,
        @Default("9090") @Range(min = 1024, max = 65535) int port,
        @Default("/metrics") String path) {

    public static final String DEFAULT_PATH = "/metrics";
    private static final String PATH_DELIMITER = "/";

    public MetricsConfig {
        if (prefix == null || prefix.isBlank()) {
            prefix = "cotani";
        }
        if (path == null || path.isBlank()) {
            path = DEFAULT_PATH;
        }
        if (!path.startsWith(PATH_DELIMITER)) {
            path = PATH_DELIMITER + path;
        }
    }
}
