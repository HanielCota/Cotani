package com.cotani.task.impl.chain;

import com.cotani.task.exception.TaskTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class TaskTimeoutController {

    <T> CompletableFuture<T> apply(CompletableFuture<T> source, Duration duration) {
        Objects.requireNonNull(source, "source");
        long timeoutNanos = validate(duration);
        return source.copy().orTimeout(timeoutNanos, TimeUnit.NANOSECONDS).exceptionallyCompose(throwable -> {
            Throwable cause = CompletionFailure.unwrap(throwable);
            if (cause instanceof TimeoutException) {
                return CompletableFuture.failedFuture(new TaskTimeoutException(duration));
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    private static long validate(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (!duration.isPositive()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("duration is too large", overflow);
        }
    }
}
