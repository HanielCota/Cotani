package net.cotani.metrics.exporter;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * Verifies HTTP behavior, lifecycle and validation of {@link PrometheusServer} that are not
 * covered by {@code PrometheusServerTest}.
 */
class PrometheusServerBehaviorTest {

    private static final String ENDPOINT_PATH = "/metrics";

    @Test
    void shouldRejectNonGetRequests() throws IOException, InterruptedException {
        try (PrometheusServer server = newServer()) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest post = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + ENDPOINT_PATH))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            assertEquals(
                    405,
                    client.send(post, HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    @Test
    void shouldBeIdempotentWhenStartedTwice() {
        PrometheusServer server = newServer();

        server.start();
        int firstPort = server.port();
        server.start();

        assertTrue(server.isRunning());
        assertEquals(firstPort, server.port());

        server.close();
    }

    @Test
    void shouldStopServingAfterClose() throws IOException, InterruptedException {
        PrometheusServer server = newServer();
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.port() + ENDPOINT_PATH))
                .GET()
                .build();

        server.close();

        assertFalse(server.isRunning());
        assertThrows(IOException.class, () -> client.send(request, HttpResponse.BodyHandlers.discarding()));
    }

    @Test
    void shouldExposeConfiguredHostAndPath() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        try (PrometheusServer server = new PrometheusServer(registry, "0.0.0.0", 0, "/scrape")) {
            assertEquals("0.0.0.0", server.host());
            assertEquals("/scrape", server.path());
        }
    }

    @Test
    void shouldRejectHostsThatResolveOutsideLoopback() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        PrometheusServer server = new PrometheusServer(registry, "192.0.2.1", 0, ENDPOINT_PATH);

        assertThrows(IllegalStateException.class, server::start);
        server.close();
    }

    @Test
    void shouldReturnConfiguredPortBeforeStart() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        PrometheusServer server = new PrometheusServer(registry, 9090, ENDPOINT_PATH);

        assertEquals(9090, server.port());

        server.close();
    }

    @Test
    void shouldFailToStartWhenPortIsAlreadyBound() throws IOException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        HttpServer blocker = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        blocker.start();
        try {
            PrometheusServer server = new PrometheusServer(
                    registry, "127.0.0.1", blocker.getAddress().getPort(), ENDPOINT_PATH);

            assertThrows(IllegalStateException.class, server::start);

            server.close();
        } finally {
            blocker.stop(0);
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullConstructorArguments() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        assertThrows(NullPointerException.class, () -> new PrometheusServer(null, 9090, ENDPOINT_PATH));
        assertThrows(NullPointerException.class, () -> new PrometheusServer(registry, null, 9090, ENDPOINT_PATH));
        assertThrows(NullPointerException.class, () -> new PrometheusServer(registry, 9090, null));
    }

    @Test
    void shouldServeMetricValuesInScrapeBody() throws IOException, InterruptedException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.counter("cotani_test_scrape").increment(3);

        try (PrometheusServer server = new PrometheusServer(registry, 0, ENDPOINT_PATH)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + ENDPOINT_PATH))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("cotani_test_scrape_total 3.0"));
        }
    }

    private static PrometheusServer newServer() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        return new PrometheusServer(registry, 0, ENDPOINT_PATH);
    }
}
