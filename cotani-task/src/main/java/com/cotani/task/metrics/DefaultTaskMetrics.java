package com.cotani.task.metrics;

import com.cotani.task.api.TaskMetadata;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class DefaultTaskMetrics implements TaskMetrics {

    private final ConcurrentHashMap<String, MetricEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void record(TaskMetadata metadata, boolean success, Duration elapsed) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(elapsed, "elapsed");

        entries.computeIfAbsent(metadata.name(), ignored -> new MetricEntry()).record(success, elapsed.toMillis());
    }

    @Override
    public TaskMetricSnapshot snapshot(String name) {
        Objects.requireNonNull(name, "name");

        MetricEntry entry = entries.get(name);

        if (entry == null) {
            return new TaskMetricSnapshot(name, 0, 0, Duration.ZERO);
        }

        return entry.toSnapshot(name);
    }

    @Override
    public TaskMetricSnapshot snapshotAll() {
        long executions = 0;
        long failures = 0;
        long totalMillis = 0;

        for (MetricEntry entry : new ArrayList<>(entries.values())) {
            executions += entry.executions();
            failures += entry.failures();
            totalMillis += entry.totalMillis();
        }

        return new TaskMetricSnapshot("all", executions, failures, Duration.ofMillis(totalMillis));
    }

    private static final class MetricEntry {
        private final LongAdder executions = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder totalMillis = new LongAdder();

        void record(boolean success, long millis) {
            executions.increment();
            totalMillis.add(millis);

            if (!success) {
                failures.increment();
            }
        }

        long executions() {
            return executions.sum();
        }

        long failures() {
            return failures.sum();
        }

        long totalMillis() {
            return totalMillis.sum();
        }

        TaskMetricSnapshot toSnapshot(String name) {
            return new TaskMetricSnapshot(name, executions(), failures(), Duration.ofMillis(totalMillis()));
        }
    }
}
