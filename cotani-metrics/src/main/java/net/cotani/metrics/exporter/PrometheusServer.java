package net.cotani.metrics.exporter;

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
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Embedded HTTP server providing a Prometheus scrape endpoint.
 */
public final class PrometheusServer implements AutoCloseable {

    private final PrometheusMeterRegistry registry;
    private final int port;
    private final String path;
    private @Nullable HttpServer server;
    private @Nullable ExecutorService executor;

    public PrometheusServer(PrometheusMeterRegistry registry, int port, String path) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.port = port;
        this.path = Objects.requireNonNull(path, "path");
    }

    public synchronized void start() {
        if (server != null) {
            return;
        }

        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
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
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] response = registry.scrape().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public int port() {
        return port;
    }

    public String path() {
        return path;
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }
}
