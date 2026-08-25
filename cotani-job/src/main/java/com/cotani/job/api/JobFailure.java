package com.cotani.job.api;

import java.util.Objects;

/** Describes a failed attempt reported to the configured failure listener. */
public record JobFailure(
        JobId jobId, String handlerName, int attempt, int maxAttempts, boolean willRetry, Throwable cause) {
    public JobFailure {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(handlerName, "handlerName");
        Objects.requireNonNull(cause, "cause");
        if (attempt <= 0 || maxAttempts <= 0 || attempt > maxAttempts) {
            throw new IllegalArgumentException("attempt must be between 1 and maxAttempts");
        }
    }
}
