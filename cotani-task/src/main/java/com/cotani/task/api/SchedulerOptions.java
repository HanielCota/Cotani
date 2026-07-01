package com.cotani.task.api;

import java.time.Duration;
import java.util.Objects;

public record SchedulerOptions(
        boolean useVirtualThreads,
        boolean cancelPaperTasksOnClose,
        Duration defaultShutdownTimeout,
        int maxConcurrentVirtualThreads) {

    public SchedulerOptions {
        Objects.requireNonNull(defaultShutdownTimeout, "defaultShutdownTimeout");

        if (maxConcurrentVirtualThreads <= 0) {
            throw new IllegalArgumentException("maxConcurrentVirtualThreads must be positive");
        }
    }

    public static SchedulerOptions defaults() {
        return new SchedulerOptions(true, true, Duration.ofSeconds(5), 256);
    }
}
