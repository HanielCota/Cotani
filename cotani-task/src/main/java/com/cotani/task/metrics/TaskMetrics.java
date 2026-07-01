package com.cotani.task.metrics;

import com.cotani.task.api.TaskMetadata;
import java.time.Duration;

public interface TaskMetrics {

    void record(TaskMetadata metadata, boolean success, Duration elapsed);

    TaskMetricSnapshot snapshot(String name);

    TaskMetricSnapshot snapshotAll();
}
