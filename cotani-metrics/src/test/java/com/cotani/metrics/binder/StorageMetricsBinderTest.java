package com.cotani.metrics.binder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.metrics.CotaniMetricsRegistry;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Hikari factory variants and null-safety of {@link StorageMetricsBinder}.
 */
class StorageMetricsBinderTest {

    private static final StorageMetricsBinder.StoragePoolStatsView POOL_STATS =
            new StorageMetricsBinder.StoragePoolStatsView(10, 5, 15, 1);

    @Test
    void shouldUseDefaultPoolNameWhenDataSourceHasNoName() {
        HikariDataSource dataSource = mock(HikariDataSource.class);

        StorageMetricsBinder binder = StorageMetricsBinder.forHikari(dataSource);
        MeterRegistry simpleRegistry = bind(binder);

        Gauge activeGauge = simpleRegistry
                .find("cotani.storage.pool.active")
                .tag("pool", "default")
                .gauge();
        assertNotNull(activeGauge);
        assertEquals(0.0, activeGauge.value(), 0.0);
    }

    @Test
    void shouldUseDataSourcePoolName() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getPoolName()).thenReturn("main_pool");

        StorageMetricsBinder binder = StorageMetricsBinder.forHikari(dataSource);
        MeterRegistry simpleRegistry = bind(binder);

        assertNotNull(simpleRegistry
                .find("cotani.storage.pool.active")
                .tag("pool", "main_pool")
                .gauge());
    }

    @Test
    void shouldReportZeroesWhenPoolMxBeanIsUnavailable() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getPoolName()).thenReturn("main_pool");

        StorageMetricsBinder binder = StorageMetricsBinder.forHikari("main_pool", dataSource);
        MeterRegistry simpleRegistry = bind(binder);

        assertEquals(0.0, gaugeValue(simpleRegistry, "cotani.storage.pool.active"), 0.0);
        assertEquals(0.0, gaugeValue(simpleRegistry, "cotani.storage.pool.idle"), 0.0);
        assertEquals(0.0, gaugeValue(simpleRegistry, "cotani.storage.pool.total"), 0.0);
        assertEquals(0.0, gaugeValue(simpleRegistry, "cotani.storage.pool.awaiting"), 0.0);
    }

    @Test
    void shouldReadValuesFromPoolMxBean() {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean mxBean = mock(HikariPoolMXBean.class);
        when(dataSource.getPoolName()).thenReturn("main_pool");
        when(dataSource.getHikariPoolMXBean()).thenReturn(mxBean);
        when(mxBean.getActiveConnections()).thenReturn(7);
        when(mxBean.getIdleConnections()).thenReturn(3);
        when(mxBean.getTotalConnections()).thenReturn(10);
        when(mxBean.getThreadsAwaitingConnection()).thenReturn(1);

        StorageMetricsBinder binder = StorageMetricsBinder.forHikari("main_pool", dataSource);
        MeterRegistry simpleRegistry = bind(binder);

        assertEquals(7.0, gaugeValue(simpleRegistry, "cotani.storage.pool.active"), 0.0);
        assertEquals(3.0, gaugeValue(simpleRegistry, "cotani.storage.pool.idle"), 0.0);
        assertEquals(10.0, gaugeValue(simpleRegistry, "cotani.storage.pool.total"), 0.0);
        assertEquals(1.0, gaugeValue(simpleRegistry, "cotani.storage.pool.awaiting"), 0.0);
    }

    @Test
    void shouldRegisterGaugesFromSupplier() {
        StorageMetricsBinder binder = new StorageMetricsBinder("main_pool", () -> POOL_STATS);
        MeterRegistry simpleRegistry = bind(binder);

        assertEquals(10.0, gaugeValue(simpleRegistry, "cotani.storage.pool.active"), 0.0);
        assertEquals(5.0, gaugeValue(simpleRegistry, "cotani.storage.pool.idle"), 0.0);
        assertEquals(15.0, gaugeValue(simpleRegistry, "cotani.storage.pool.total"), 0.0);
        assertEquals(1.0, gaugeValue(simpleRegistry, "cotani.storage.pool.awaiting"), 0.0);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullDataSourceInForHikari() {
        assertThrows(NullPointerException.class, () -> StorageMetricsBinder.forHikari(null));
        assertThrows(NullPointerException.class, () -> StorageMetricsBinder.forHikari("main_pool", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPoolNameInForHikari() {
        HikariDataSource dataSource = mock(HikariDataSource.class);

        assertThrows(NullPointerException.class, () -> StorageMetricsBinder.forHikari(null, dataSource));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new StorageMetricsBinder(null, () -> POOL_STATS));
        assertThrows(NullPointerException.class, () -> new StorageMetricsBinder("main_pool", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullRegistryOnBindTo() {
        StorageMetricsBinder binder = new StorageMetricsBinder("main_pool", () -> POOL_STATS);

        assertThrows(NullPointerException.class, () -> binder.bindTo(null));
    }

    private static MeterRegistry bind(StorageMetricsBinder binder) {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "cotani");
        registry.register(binder);
        return simpleRegistry;
    }

    private static double gaugeValue(MeterRegistry registry, String name) {
        Gauge gauge = registry.find(name).gauge();
        assertNotNull(gauge, "gauge " + name + " was not registered");
        return gauge.value();
    }
}
