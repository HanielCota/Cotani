package com.cotani.metrics.exporter;

import com.cotani.api.InternalApi;
import com.cotani.metrics.config.MetricsConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
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
    private final Semaphore scrapePermits = new Semaphore(4);
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

        try {
            var bindAddress = requireLoopbackAddress(host);
            HttpServer http = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
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

            if (!scrapePermits.tryAcquire()) {
                exchange.sendResponseHeaders(429, -1);
                return;
            }

            try {
                byte[] response = registry.scrape().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } finally {
                scrapePermits.release();
            }
        } finally {
            exchange.close();
        }
    }

    private static InetAddress requireLoopbackAddress(String configuredHost) {
        try {
            var addresses = InetAddress.getAllByName(configuredHost.strip());
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !address.isLoopbackAddress())) {
                throw new IllegalStateException(
                        "Prometheus scrape server refuses non-loopback bind '" + configuredHost + "'; use 127.0.0.1");
            }
            return addresses[0];
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Invalid Prometheus bind host '" + configuredHost + "'", exception);
        }
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
