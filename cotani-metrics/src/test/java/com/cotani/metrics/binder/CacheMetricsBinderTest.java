package com.cotani.metrics.binder;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.cache.stats.CacheStatsView;
import com.cotani.metrics.CotaniMetricsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Verifies null-safety and snapshot memoization of {@link CacheMetricsBinder}.
 */
class CacheMetricsBinderTest {

    @Test
    void shouldShareSingleSnapshotAcrossAllGauges() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "cotani");
        AtomicInteger supplierCalls = new AtomicInteger();
        CacheMetricsBinder binder = new CacheMetricsBinder("player_cache", () -> {
            supplierCalls.incrementAndGet();
            return new CacheStatsView(100L, 80L, 20L, 0.8, 5L, 2);
        });

        registry.register(binder);

        assertEquals(100.0, gaugeValue(simpleRegistry, "cotani.cache.size"), 0.0);
        assertEquals(80.0, gaugeValue(simpleRegistry, "cotani.cache.hits"), 0.0);
        assertEquals(20.0, gaugeValue(simpleRegistry, "cotani.cache.misses"), 0.0);
        assertEquals(0.8, gaugeValue(simpleRegistry, "cotani.cache.hit_rate"), 0.0);
        assertEquals(5.0, gaugeValue(simpleRegistry, "cotani.cache.evictions"), 0.0);
        assertEquals(2.0, gaugeValue(simpleRegistry, "cotani.cache.dirty"), 0.0);
        assertEquals(1, supplierCalls.get(), "all gauges must read the same memoized snapshot");
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullCacheName() {
        assertThrows(NullPointerException.class, () -> new CacheMetricsBinder(null, CacheMetricsBinderTest::stats));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullStatsSupplier() {
        assertThrows(NullPointerException.class, () -> new CacheMetricsBinder("player_cache", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullRegistryOnBindTo() {
        CacheMetricsBinder binder = new CacheMetricsBinder("player_cache", CacheMetricsBinderTest::stats);

        assertThrows(NullPointerException.class, () -> binder.bindTo(null));
    }

    private static CacheStatsView stats() {
        return new CacheStatsView(0L, 0L, 0L, 0.0, 0L, 0);
    }

    private static double gaugeValue(MeterRegistry registry, String name) {
        var gauge = registry.find(name).gauge();
        assertNotNull(gauge, "gauge " + name + " was not registered");
        return gauge.value();
    }
}
