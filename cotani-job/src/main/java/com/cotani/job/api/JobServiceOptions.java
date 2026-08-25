package com.cotani.job.api;

import java.time.Duration;
import java.util.Objects;

/** Runtime policies applied to every job handler invocation. */
public record JobServiceOptions(Duration handlerTimeout, JobFailureListener failureListener, int maxRecoveryBatch) {
    public static final int DEFAULT_MAX_RECOVERY_BATCH = 256;
    public static final int MAX_RECOVERY_BATCH = 10_000;

    public JobServiceOptions(Duration handlerTimeout, JobFailureListener failureListener) {
        this(handlerTimeout, failureListener, DEFAULT_MAX_RECOVERY_BATCH);
    }

    public JobServiceOptions {
        Objects.requireNonNull(handlerTimeout, "handlerTimeout");
        Objects.requireNonNull(failureListener, "failureListener");
        if (handlerTimeout.isZero() || handlerTimeout.isNegative() || timeoutMillis(handlerTimeout) <= 0) {
            throw new IllegalArgumentException("handlerTimeout must be positive");
        }
        if (maxRecoveryBatch <= 0 || maxRecoveryBatch > MAX_RECOVERY_BATCH) {
            throw new IllegalArgumentException("maxRecoveryBatch must be between 1 and " + MAX_RECOVERY_BATCH);
        }
    }

    public static JobServiceOptions defaults() {
        return new JobServiceOptions(Duration.ofSeconds(30), _ -> {}, DEFAULT_MAX_RECOVERY_BATCH);
    }

    public JobServiceOptions withHandlerTimeout(Duration timeout) {
        return new JobServiceOptions(timeout, failureListener, maxRecoveryBatch);
    }

    public JobServiceOptions withFailureListener(JobFailureListener listener) {
        return new JobServiceOptions(handlerTimeout, listener, maxRecoveryBatch);
    }

    public JobServiceOptions withMaxRecoveryBatch(int batchSize) {
        return new JobServiceOptions(handlerTimeout, failureListener, batchSize);
    }

    private static long timeoutMillis(Duration timeout) {
        try {
            return timeout.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("handlerTimeout is too large", overflow);
        }
    }
}
