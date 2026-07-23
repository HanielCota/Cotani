package com.cotani.task.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersistentTask that)) return false;
        return Objects.equals(id, that.id)
                && Objects.equals(taskName, that.taskName)
                && Objects.equals(scheduledAt, that.scheduledAt)
                && Objects.equals(delay, that.delay)
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, taskName, scheduledAt, delay);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public String toString() {
        return "PersistentTask[id=" + id + ", taskName=" + taskName + ", scheduledAt=" + scheduledAt + ", delay="
                + delay + ", payload=" + Arrays.toString(payload) + "]";
    }
}
