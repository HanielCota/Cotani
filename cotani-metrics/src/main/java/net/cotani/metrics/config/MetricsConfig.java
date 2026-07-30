package net.cotani.metrics.config;

import com.cotani.config.annotation.Default;
import com.cotani.config.annotation.Range;

/**
 * Immutable configuration settings for Cotani metrics collection and export.
 *
 * @param enabled whether metrics collection is enabled
 * @param prefix  prefix for metric names
 * @param host    network interface or address used by the scrape server
 * @param port    HTTP port for Prometheus scraping endpoint
 * @param path    path for Prometheus scraping endpoint
 */
public record MetricsConfig(
        @Default("false") boolean enabled,
        @Default("cotani") String prefix,
        @Default("127.0.0.1") String host,
        @Default("9090") @Range(min = 1024, max = 65535) int port,
        @Default("/metrics") String path) {

    public static final String DEFAULT_PATH = "/metrics";
    public static final String DEFAULT_HOST = "127.0.0.1";
    private static final String PATH_DELIMITER = "/";

    public MetricsConfig(boolean enabled, String prefix, int port, String path) {
        this(enabled, prefix, DEFAULT_HOST, port, path);
    }

    public MetricsConfig {
        if (prefix == null || prefix.isBlank()) {
            prefix = "cotani";
        }
        if (path == null || path.isBlank()) {
            path = DEFAULT_PATH;
        }
        if (host == null || host.isBlank()) {
            host = DEFAULT_HOST;
        }
        if (!path.startsWith(PATH_DELIMITER)) {
            path = PATH_DELIMITER + path;
        }
    }
}
