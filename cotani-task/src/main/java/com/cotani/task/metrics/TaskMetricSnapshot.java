package com.cotani.task.metrics;

import java.time.Duration;

public record TaskMetricSnapshot(String name, long executions, long failures, Duration totalElapsed) {

    public Duration averageElapsed() {
        if (executions == 0) {
            return Duration.ZERO;
        }

        return Duration.ofMillis(totalElapsed.toMillis() / executions);
    }
}
