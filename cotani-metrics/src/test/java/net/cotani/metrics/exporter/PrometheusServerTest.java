package net.cotani.metrics.exporter;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class PrometheusServerTest {

    @Test
    void prometheusServerServesMetricsEndpoint() throws IOException, InterruptedException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.counter("test.requests").increment();

        try (PrometheusServer server = new PrometheusServer(registry, 0, "/metrics")) {
            server.start();
            assertTrue(server.isRunning());

            assertTrue(server.port() >= 0);

            // When port was 0, we can read actual port bound by server if available,
            // but for tests let's test on fixed dynamic port or server.port()
        }
    }

    @Test
    void prometheusServerRespondsToHttpGet() throws IOException, InterruptedException {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        registry.counter("cotani.test.counter").increment(3);

        try (PrometheusServer server = new PrometheusServer(registry, 0, "/metrics")) {
            server.start();
            assertTrue(server.isRunning());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/metrics"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").isPresent());
            assertTrue(response.headers().firstValue("Content-Type").get().contains("text/plain"));
            assertTrue(response.body().contains("cotani_test_counter"));

            HttpRequest prefixedPath = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/metrics/private"))
                    .GET()
                    .build();
            assertEquals(
                    404,
                    client.send(prefixedPath, HttpResponse.BodyHandlers.discarding())
                            .statusCode());
        }
    }
}
