package com.cotani.task.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.TaskMetadata;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DefaultTaskMetricsTest {

    @Test
    void recordsExecutionsAndFailures() {
        DefaultTaskMetrics metrics = new DefaultTaskMetrics();
        TaskMetadata metadata = TaskMetadata.named("sample-task", ExecutionTarget.async());

        metrics.record(metadata, true, Duration.ofMillis(10));
        metrics.record(metadata, true, Duration.ofMillis(20));
        metrics.record(metadata, false, Duration.ofMillis(30));

        TaskMetricSnapshot snapshot = metrics.snapshot("sample-task");

        assertEquals(3, snapshot.executions());
        assertEquals(1, snapshot.failures());
        assertEquals(Duration.ofMillis(60), snapshot.totalElapsed());
        assertEquals(Duration.ofMillis(20), snapshot.averageElapsed());
    }

    @Test
    void snapshotReturnsZeroForUnknownTask() {
        DefaultTaskMetrics metrics = new DefaultTaskMetrics();

        TaskMetricSnapshot snapshot = metrics.snapshot("unknown");

        assertEquals("unknown", snapshot.name());
        assertEquals(0, snapshot.executions());
        assertEquals(0, snapshot.failures());
        assertEquals(Duration.ZERO, snapshot.totalElapsed());
        assertEquals(Duration.ZERO, snapshot.averageElapsed());
    }

    @Test
    void snapshotAllAggregatesAllTasks() {
        DefaultTaskMetrics metrics = new DefaultTaskMetrics();

        metrics.record(TaskMetadata.named("a", ExecutionTarget.async()), true, Duration.ofMillis(10));
        metrics.record(TaskMetadata.named("b", ExecutionTarget.async()), false, Duration.ofMillis(20));

        TaskMetricSnapshot snapshot = metrics.snapshotAll();

        assertEquals("all", snapshot.name());
        assertEquals(2, snapshot.executions());
        assertEquals(1, snapshot.failures());
        assertEquals(Duration.ofMillis(30), snapshot.totalElapsed());
        assertTrue(snapshot.averageElapsed().toMillis() > 0);
    }
}
