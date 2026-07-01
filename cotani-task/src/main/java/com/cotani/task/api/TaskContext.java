package com.cotani.task.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class TaskContext {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final TaskMetadata metadata;
    private final Instant startedAt;
    private final long startNanos;

    private TaskContext(TaskMetadata metadata, Instant startedAt, long startNanos) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.startNanos = startNanos;
    }

    public static TaskContext start(TaskMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        return new TaskContext(metadata, Instant.now(), System.nanoTime());
    }

    public TaskMetadata metadata() {
        return metadata;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Duration elapsed() {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    public long elapsedMillis() {
        return (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
    }
}
