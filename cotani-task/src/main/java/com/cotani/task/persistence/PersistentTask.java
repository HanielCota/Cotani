package com.cotani.task.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("ArrayRecordComponent")
public record PersistentTask(UUID id, String taskName, Instant scheduledAt, Duration delay, byte[] payload) {

    public PersistentTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(payload, "payload");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
