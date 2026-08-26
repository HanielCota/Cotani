package com.cotani.metrics.binder;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.metrics.CotaniMetricsRegistry;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.metrics.TaskMetricSnapshot;
import com.cotani.task.metrics.TaskMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * Verifies the snapshot supplier constructor and null-safety of {@link TaskMetricsBinder}.
 */
class TaskMetricsBinderTest {

    @Test
    void shouldRegisterGaugesFromSnapshotSupplier() {
        AtomicLong executions = new AtomicLong(7);
        AtomicLong failures = new AtomicLong(1);
        TaskMetricsBinder binder = new TaskMetricsBinder(
                "user_save",
                () -> new TaskMetricSnapshot("user_save", executions.get(), failures.get(), Duration.ofSeconds(5)));
        MeterRegistry simpleRegistry = bind(binder);

        assertEquals(7.0, gaugeValue(simpleRegistry, "cotani.task.executions"), 0.0);
        assertEquals(1.0, gaugeValue(simpleRegistry, "cotani.task.failures"), 0.0);
        assertEquals(714.0, gaugeValue(simpleRegistry, "cotani.task.execution_time.avg.ms"), 0.0);

        executions.set(10);
        failures.set(2);

        assertEquals(10.0, gaugeValue(simpleRegistry, "cotani.task.executions"), 0.0);
        assertEquals(2.0, gaugeValue(simpleRegistry, "cotani.task.failures"), 0.0);
        assertEquals(500.0, gaugeValue(simpleRegistry, "cotani.task.execution_time.avg.ms"), 0.0);
    }

    @Test
    void shouldReportZeroAverageWhenNoExecutions() {
        TaskMetricsBinder binder = new TaskMetricsBinder(
                "idle_task", () -> new TaskMetricSnapshot("idle_task", 0L, 0L, Duration.ofSeconds(5)));
        MeterRegistry simpleRegistry = bind(binder);

        assertEquals(0.0, gaugeValue(simpleRegistry, "cotani.task.executions"), 0.0);
        assertEquals(0.0, gaugeValue(simpleRegistry, "cotani.task.failures"), 0.0);
        assertEquals(0.0, gaugeValue(simpleRegistry, "cotani.task.execution_time.avg.ms"), 0.0);
    }

    @Test
    void shouldUseSnapshotFromTaskMetricsInstance() {
        TaskMetricSnapshot snapshot = new TaskMetricSnapshot("user_save", 50L, 2L, Duration.ofSeconds(5));
        TaskMetrics taskMetrics = new TaskMetrics() {
            @Override
            public void record(TaskMetadata metadata, boolean success, Duration elapsed) {
                // Intentionally empty test double
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

        TaskMetricsBinder binder = new TaskMetricsBinder(taskMetrics, "user_save");
        MeterRegistry simpleRegistry = bind(binder);

        assertEquals(50.0, gaugeValue(simpleRegistry, "cotani.task.executions"), 0.0);
        assertEquals(2.0, gaugeValue(simpleRegistry, "cotani.task.failures"), 0.0);
        assertEquals(100.0, gaugeValue(simpleRegistry, "cotani.task.execution_time.avg.ms"), 0.0);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullTaskName() {
        assertThrows(
                NullPointerException.class,
                () -> new TaskMetricsBinder(null, () -> new TaskMetricSnapshot("user_save", 0L, 0L, Duration.ZERO)));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullSnapshotSupplier() {
        assertThrows(NullPointerException.class, () -> new TaskMetricsBinder("user_save", null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullTaskMetricsInstance() {
        assertThrows(NullPointerException.class, () -> new TaskMetricsBinder(null, "user_save"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullRegistryOnBindTo() {
        TaskMetricsBinder binder =
                new TaskMetricsBinder("user_save", () -> new TaskMetricSnapshot("user_save", 0L, 0L, Duration.ZERO));

        assertThrows(NullPointerException.class, () -> binder.bindTo(null));
    }

    private static MeterRegistry bind(TaskMetricsBinder binder) {
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
