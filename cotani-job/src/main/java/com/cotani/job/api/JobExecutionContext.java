package com.cotani.job.api;

import java.util.Objects;

/** Immutable context supplied to a handler for one attempt. */
@SuppressWarnings("ArrayRecordComponent")
public record JobExecutionContext(
        JobId jobId, JobExecutionId executionId, String handlerName, int attempt, byte[] payload) {
    public JobExecutionContext {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(handlerName, "handlerName");
        Objects.requireNonNull(payload, "payload");
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        payload = payload.clone();
    }

    /** Compatibility constructor for manually created contexts. */
    public JobExecutionContext(JobId jobId, String handlerName, int attempt, byte[] payload) {
        this(jobId, JobExecutionId.random(), handlerName, attempt, payload);
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
