package net.cotani.metrics;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.cotani.metrics.api.MeterBinder;
import org.junit.jupiter.api.Test;

class CotaniMetricsRegistryTest {

    @Test
    void registersCounterGaugeAndTimer() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");

        assertTrue(registry.isEnabled());

        registry.counter("events", "type", "login");
        registry.counter("events", 5.0, "type", "logout");

        Counter loginCounter =
                simpleRegistry.find("app.events").tag("type", "login").counter();
        Counter logoutCounter =
                simpleRegistry.find("app.events").tag("type", "logout").counter();

        assertNotNull(loginCounter);
        assertNotNull(logoutCounter);
        assertEquals(1.0, loginCounter.count());
        assertEquals(5.0, logoutCounter.count());

        AtomicInteger activeUsers = new AtomicInteger(42);
        registry.gauge("active_users", activeUsers::get, "region", "us");

        Gauge gauge =
                simpleRegistry.find("app.active_users").tag("region", "us").gauge();
        assertNotNull(gauge);
        assertEquals(42.0, gauge.value());

        registry.timer("request_duration", Duration.ofMillis(250), "path", "/api");

        Timer timer =
                simpleRegistry.find("app.request_duration").tag("path", "/api").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertEquals(250.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void BindsMeterBinder() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");

        MeterBinder binder = reg -> reg.counter("custom_metric", "bound", "true");
        registry.register(binder);

        Counter counter =
                simpleRegistry.find("app.custom_metric").tag("bound", "true").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void exposesMeterRegistry() {
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry("app");
        Optional<MeterRegistry> meterRegistry = registry.meterRegistry();
        assertTrue(meterRegistry.isPresent());
        registry.close();
    }
}
