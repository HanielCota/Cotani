package net.cotani.metrics.exporter;

import com.cotani.api.InternalApi;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.cotani.metrics.config.MetricsConfig;
import org.jspecify.annotations.Nullable;

/**
 * Embedded HTTP server providing a Prometheus scrape endpoint.
 */
@InternalApi
public final class PrometheusServer implements AutoCloseable {
    private final PrometheusMeterRegistry registry;
    private final String host;
    private final int port;
    private final String path;
    private @Nullable HttpServer server;
    private @Nullable ExecutorService executor;

    public PrometheusServer(PrometheusMeterRegistry registry, int port, String path) {
        this(registry, MetricsConfig.DEFAULT_HOST, port, path);
    }

    public PrometheusServer(PrometheusMeterRegistry registry, String host, int port, String path) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.path = Objects.requireNonNull(path, "path");
    }

    public synchronized void start() {
        if (server != null) {
            return;
        }

        if (isPublicBind(host)) {
            throw new IllegalStateException(
                    "Prometheus scrape server refuses non-loopback bind '" + host + "'; use 127.0.0.1");
        }

        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(host, port), 0);
            ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
            http.setExecutor(exec);
            http.createContext(path, this::handleScrape);
            http.start();
            this.server = http;
            this.executor = exec;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Prometheus HTTP server on port " + port, e);
        }
    }

    private void handleScrape(HttpExchange exchange) throws IOException {
        try {
            if (!path.equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            byte[] response = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        } finally {
            exchange.close();
        }
    }

    private static boolean isPublicBind(String bindHost) {
        var normalized = bindHost.strip();
        return "0.0.0.0".equals(normalized)
                || "::".equals(normalized)
                || "*".equals(normalized)
                || "0:0:0:0:0:0:0:0".equals(normalized);
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized int port() {
        return server == null ? port : server.getAddress().getPort();
    }

    public String host() {
        return host;
    }

    public String path() {
        return path;
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
