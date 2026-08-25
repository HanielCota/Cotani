package com.cotani.job.api;

import java.util.Objects;
import java.util.UUID;

/** Identifies one occurrence of a logical job, including all of its retries. */
public record JobExecutionId(UUID value) {
    public JobExecutionId {
        Objects.requireNonNull(value, "value");
    }

    public static JobExecutionId random() {
        return new JobExecutionId(UUID.randomUUID());
    }
}
