package net.cotani.metrics.binder;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.cache.stats.CacheStatsView;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.metrics.TaskMetricSnapshot;
import com.cotani.task.metrics.TaskMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import net.cotani.metrics.CotaniMetricsRegistry;
import org.junit.jupiter.api.Test;

class MetricsBinderTest {
    @Test
    void cacheMetricsBinderRegistersGauges() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "cotani");

        CacheStatsView stats = new CacheStatsView(100L, 80L, 20L, 0.8, 5L, 2);
        CacheMetricsBinder binder = new CacheMetricsBinder("player_cache", () -> stats);
        registry.register(binder);

        Gauge sizeGauge = simpleRegistry
                .find("cotani.cache.size")
                .tag("cache", "player_cache")
                .gauge();
        Gauge hitsGauge = simpleRegistry
                .find("cotani.cache.hits")
                .tag("cache", "player_cache")
                .gauge();
        Gauge missesGauge = simpleRegistry
                .find("cotani.cache.misses")
                .tag("cache", "player_cache")
                .gauge();
        Gauge rateGauge = simpleRegistry
                .find("cotani.cache.hit_rate")
                .tag("cache", "player_cache")
                .gauge();
        Gauge evictionsGauge = simpleRegistry
                .find("cotani.cache.evictions")
                .tag("cache", "player_cache")
                .gauge();
        Gauge dirtyGauge = simpleRegistry
                .find("cotani.cache.dirty")
                .tag("cache", "player_cache")
                .gauge();

        assertNotNull(sizeGauge);
        assertNotNull(hitsGauge);
        assertNotNull(missesGauge);
        assertNotNull(rateGauge);
        assertNotNull(evictionsGauge);
        assertNotNull(dirtyGauge);

        assertEquals(100.0, sizeGauge.value());
        assertEquals(80.0, hitsGauge.value());
        assertEquals(20.0, missesGauge.value());
        assertEquals(0.8, rateGauge.value());
        assertEquals(5.0, evictionsGauge.value());
        assertEquals(2.0, dirtyGauge.value());
    }

    @Test
    void storageMetricsBinderRegistersGauges() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "cotani");

        StorageMetricsBinder.StoragePoolStatsView poolStats =
                new StorageMetricsBinder.StoragePoolStatsView(10, 5, 15, 0);
        StorageMetricsBinder binder = new StorageMetricsBinder("main_pool", () -> poolStats);
        registry.register(binder);

        Gauge activeGauge = simpleRegistry
                .find("cotani.storage.pool.active")
                .tag("pool", "main_pool")
                .gauge();
        Gauge idleGauge = simpleRegistry
                .find("cotani.storage.pool.idle")
                .tag("pool", "main_pool")
                .gauge();
        Gauge totalGauge = simpleRegistry
                .find("cotani.storage.pool.total")
                .tag("pool", "main_pool")
                .gauge();
        Gauge awaitingGauge = simpleRegistry
                .find("cotani.storage.pool.awaiting")
                .tag("pool", "main_pool")
                .gauge();

        assertNotNull(activeGauge);
        assertNotNull(idleGauge);
        assertNotNull(totalGauge);
        assertNotNull(awaitingGauge);

        assertEquals(10.0, activeGauge.value());
        assertEquals(5.0, idleGauge.value());
        assertEquals(15.0, totalGauge.value());
        assertEquals(0.0, awaitingGauge.value());
    }

    @Test
    void taskMetricsBinderRegistersGauges() {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "cotani");

        TaskMetricSnapshot snapshot = new TaskMetricSnapshot("user_save", 50L, 2L, Duration.ofSeconds(5));
        TaskMetrics mockTaskMetrics = new TaskMetrics() {
            @Override
            public void record(TaskMetadata metadata, boolean success, Duration elapsed) {
                // Intentionally empty for test mock
            }

            @Override
            public TaskMetricSnapshot snapshot(String name) {
                return snapshot;
            }

            @Override
            public TaskMetricSnapshot snapshotAll() {
                return snapshot;
            }
        };

        TaskMetricsBinder binder = new TaskMetricsBinder(mockTaskMetrics, "user_save");
        registry.register(binder);

        Gauge executionsGauge = simpleRegistry
                .find("cotani.task.executions")
                .tag("task", "user_save")
                .gauge();
        Gauge failuresGauge = simpleRegistry
                .find("cotani.task.failures")
                .tag("task", "user_save")
                .gauge();
        Gauge avgTimeGauge = simpleRegistry
                .find("cotani.task.execution_time.avg.ms")
                .tag("task", "user_save")
                .gauge();

        assertNotNull(executionsGauge);
        assertNotNull(failuresGauge);
        assertNotNull(avgTimeGauge);

        assertEquals(50.0, executionsGauge.value());
        assertEquals(2.0, failuresGauge.value());
        assertEquals(100.0, avgTimeGauge.value());
    }
}
