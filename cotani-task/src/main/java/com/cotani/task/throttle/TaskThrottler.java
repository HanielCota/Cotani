package com.cotani.task.throttle;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class TaskThrottler {

    private static final int DEFAULT_MAX_ATTEMPTS = 10;

    private final PaperTaskScheduler scheduler;
    private final int maxAttempts;

    public TaskThrottler(PaperTaskScheduler scheduler) {
        this(scheduler, DEFAULT_MAX_ATTEMPTS);
    }

    public TaskThrottler(PaperTaskScheduler scheduler, int maxAttempts) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }

        this.maxAttempts = maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public <T> TaskChain<T> throttle(Supplier<T> supplier, RateLimiter limiter) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(limiter, "limiter");

        return scheduler.chain(throttleStage(supplier, limiter, 1));
    }

    private <T> CompletionStage<T> throttleStage(Supplier<T> supplier, RateLimiter limiter, int attempt) {
        return scheduler
                .supplyAsync(() -> {
                    if (limiter.tryAcquire()) {
                        return supplier.get();
                    }
                    throw new RateLimitRejectedException(limiter.retryDelay());
                })
                .toCompletionStage()
                .exceptionallyCompose(error -> {
                    Throwable cause = unwrap(error);
                    if (cause instanceof RateLimitRejectedException rejected) {
                        if (attempt >= maxAttempts) {
                            return CompletableFuture.failedStage(new RateLimitExceededException(maxAttempts));
                        }

                        return scheduler
                                .delayAsync(rejected.retryDelay())
                                .thenCompose(_ -> throttleStage(supplier, limiter, attempt + 1));
                    }
                    return CompletableFuture.failedStage(error);
                });
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) {
            Throwable cause = current.getCause();
            if (cause == null || cause.equals(current)) {
                break;
            }
            current = cause;
        }
        return current;
    }

    private static final class RateLimitRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Duration retryDelay;

        RateLimitRejectedException(Duration retryDelay) {
            this.retryDelay = retryDelay;
        }

        Duration retryDelay() {
            return retryDelay;
        }
    }
}
