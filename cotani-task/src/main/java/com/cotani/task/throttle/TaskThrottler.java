package com.cotani.task.throttle;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
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
                    if (error instanceof RateLimitRejectedException rejected) {
                        return scheduler
                                .delayAsync(rejected.retryDelay())
                                .thenCompose(_ -> throttleStage(supplier, limiter));
                    }
                    return CompletableFuture.failedStage(error);
                });
    }

    @SuppressWarnings({"serial"})
    private static final class RateLimitRejectedException extends RuntimeException {
        private final java.time.Duration retryDelay;

        RateLimitRejectedException(java.time.Duration retryDelay) {
            this.retryDelay = retryDelay;
        }

        java.time.Duration retryDelay() {
            return retryDelay;
        }
    }
}
