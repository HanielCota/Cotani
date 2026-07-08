package com.cotani.task.throttle;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class TaskThrottler {

    private final PaperTaskScheduler scheduler;

    public TaskThrottler(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public <T> TaskChain<T> throttle(Supplier<T> supplier, RateLimiter limiter) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(limiter, "limiter");

        return scheduler.chain(throttleStage(supplier, limiter));
    }

    private <T> CompletionStage<T> throttleStage(Supplier<T> supplier, RateLimiter limiter) {
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
                        return scheduler
                                .delayAsync(rejected.retryDelay())
                                .thenCompose(_ -> throttleStage(supplier, limiter));
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
