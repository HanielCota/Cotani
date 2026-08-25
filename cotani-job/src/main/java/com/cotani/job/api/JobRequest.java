package com.cotani.job.api;

import com.cotani.task.persistence.PersistentTask;
import java.time.Duration;
import java.util.Objects;

/** Immutable definition used to schedule one logical job. */
@SuppressWarnings("ArrayRecordComponent")
public record JobRequest(String handlerName, byte[] payload, JobSchedule schedule, JobRetryPolicy retryPolicy) {
    public static final int MAX_HANDLER_NAME_LENGTH = 96;

    public JobRequest {
        Objects.requireNonNull(handlerName, "handlerName");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (handlerName.isBlank() || handlerName.length() > MAX_HANDLER_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "handlerName must be non-blank and at most " + MAX_HANDLER_NAME_LENGTH + " characters");
        }
        if (handlerName.indexOf(':') >= 0 || handlerName.indexOf('\n') >= 0 || handlerName.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("handlerName must not contain ':', '\\n' or '\\r'");
        }
        if (payload.length > PersistentTask.MAX_PAYLOAD_BYTES / 2) {
            throw new IllegalArgumentException("payload is too large for persistent job metadata");
        }
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public static JobRequest once(String handlerName, byte[] payload, Duration delay) {
        return new JobRequest(handlerName, payload, new JobSchedule.Once(delay), JobRetryPolicy.defaults());
    }

    public static JobRequest recurring(String handlerName, byte[] payload, Duration initialDelay, Duration interval) {
        return new JobRequest(
                handlerName, payload, new JobSchedule.Recurring(initialDelay, interval), JobRetryPolicy.defaults());
    }

    public JobRequest withRetryPolicy(JobRetryPolicy policy) {
        return new JobRequest(handlerName, payload, schedule, policy);
    }
}
