package net.cotani.metrics;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.cotani.metrics.api.MeterBinder;
import org.junit.jupiter.api.Test;

/**
 * Verifies naming, tag handling, validation, cardinality and lifecycle behavior of
 * {@link CotaniMetricsRegistry}.
 */
class CotaniMetricsRegistryBehaviorTest {

    @Test
    void shouldRejectNegativeCounterAmount() {
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");

        assertThrows(IllegalArgumentException.class, () -> registry.counter("events", -1.0));
    }

    @Test
    void shouldAcceptZeroCounterAmount() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");

        registry.counter("events", 0.0);

        Counter counter = simpleRegistry.find("app.events").counter();
        assertNotNull(counter);
        assertEquals(0.0, counter.count(), 0.0);
    }

    @Test
    void shouldNotDoublePrefixNameThatAlreadyCarriesPrefix() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");

        registry.counter("app.events");

        assertNotNull(simpleRegistry.find("app.events").counter());
        assertNull(simpleRegistry.find("app.app.events").counter());
    }

    @Test
    void shouldUsePlainNameWhenPrefixIsBlank() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "   ");

        registry.counter("events");

        assertNotNull(simpleRegistry.find("events").counter());
    }

    @Test
    void shouldPadOddTagListsWithEmptyValue() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");

        registry.counter("events", "only-key");

        Counter counter = simpleRegistry.find("app.events").tag("only-key", "").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.0);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullMetricName() {
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");

        assertThrows(NullPointerException.class, () -> registry.counter(null));
        assertThrows(NullPointerException.class, () -> registry.timer(null, Duration.ofSeconds(1)));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullGaugeSupplier() {
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");

        assertThrows(NullPointerException.class, () -> registry.gauge("queue", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullTimerDuration() {
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");

        assertThrows(NullPointerException.class, () -> registry.timer("latency", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullBinderOnRegister() {
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");

        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullMeterRegistryInConstructor() {
        assertThrows(NullPointerException.class, () -> new CotaniMetricsRegistry(null, "app"));
    }

    @Test
    void shouldExposePrometheusRegistryOnlyWhenPrometheusBacked() {
        CotaniMetricsRegistry prometheusBacked = new CotaniMetricsRegistry("app");
        CotaniMetricsRegistry simpleBacked = new CotaniMetricsRegistry(new SimpleMeterRegistry(), "app");

        assertTrue(prometheusBacked.prometheusRegistry().isPresent());
        assertTrue(simpleBacked.prometheusRegistry().isEmpty());

        prometheusBacked.close();
    }

    @Test
    void shouldIsolateMetersAcrossRegistryInstances() {
        MeterRegistry firstRegistry = new SimpleMeterRegistry();
        MeterRegistry secondRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry first = new CotaniMetricsRegistry(firstRegistry, "app");
        CotaniMetricsRegistry second = new CotaniMetricsRegistry(secondRegistry, "app");

        first.counter("events", "instance", "one");
        second.counter("events", "instance", "two");

        Counter firstCounter =
                firstRegistry.find("app.events").tag("instance", "one").counter();
        Counter secondCounter =
                secondRegistry.find("app.events").tag("instance", "two").counter();
        assertNotNull(firstCounter);
        assertNotNull(secondCounter);
        assertEquals(1.0, firstCounter.count(), 0.0);
        assertNull(firstRegistry.find("app.events").tag("instance", "two").counter());
        assertEquals(1.0, secondCounter.count(), 0.0);
        assertNull(secondRegistry.find("app.events").tag("instance", "one").counter());
    }

    @Test
    void shouldSupportHighTagCardinality() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");

        for (int i = 0; i < 200; i++) {
            registry.counter("events", "region", "region-" + i, "server", "server-" + i, "status", "ok");
        }

        assertEquals(200, simpleRegistry.getMeters().size());
        Counter lastCounter = simpleRegistry
                .find("app.events")
                .tag("region", "region-199")
                .tag("server", "server-199")
                .counter();
        assertNotNull(lastCounter);
        assertEquals(1.0, lastCounter.count(), 0.0);
    }

    @Test
    void shouldReflectGaugeSupplierChanges() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");
        AtomicInteger value = new AtomicInteger(1);

        registry.gauge("active_users", value::get);
        Gauge gauge = simpleRegistry.find("app.active_users").gauge();
        assertNotNull(gauge);
        assertEquals(1.0, gauge.value(), 0.0);

        value.set(42);

        assertEquals(42.0, gauge.value(), 0.0);
    }

    @Test
    void shouldDelegateCounterBindingToBinder() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");
        MeterBinder binder = target -> target.counter("custom_metric", "bound", "true");

        registry.register(binder);

        Counter counter =
                simpleRegistry.find("app.custom_metric").tag("bound", "true").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count(), 0.0);
    }
}
