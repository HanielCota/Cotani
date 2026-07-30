package com.cotani.task.throttle;

import com.cotani.task.api.DelayedTaskScheduler;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import com.cotani.task.api.TaskChainFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class TaskThrottler {

    private static final int DEFAULT_MAX_ATTEMPTS = 10;

    private final TaskChainFactory chainFactory;
    private final DelayedTaskScheduler delays;
    private final int maxAttempts;

    private TaskThrottler(PaperTaskScheduler scheduler) {
        this(scheduler, scheduler, DEFAULT_MAX_ATTEMPTS);
    }

    private TaskThrottler(PaperTaskScheduler scheduler, int maxAttempts) {
        this(scheduler, scheduler, maxAttempts);
    }

    private TaskThrottler(TaskChainFactory chainFactory, DelayedTaskScheduler delays) {
        this(chainFactory, delays, DEFAULT_MAX_ATTEMPTS);
    }

    private TaskThrottler(TaskChainFactory chainFactory, DelayedTaskScheduler delays, int maxAttempts) {
        this.chainFactory = Objects.requireNonNull(chainFactory, "chainFactory");
        this.delays = Objects.requireNonNull(delays, "delays");

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }

        this.maxAttempts = maxAttempts;
    }

    public static TaskThrottler create(PaperTaskScheduler scheduler) {
        return new TaskThrottler(scheduler);
    }

    public static TaskThrottler create(PaperTaskScheduler scheduler, int maxAttempts) {
        return new TaskThrottler(scheduler, maxAttempts);
    }

    public static TaskThrottler create(TaskChainFactory chainFactory, DelayedTaskScheduler delays) {
        return new TaskThrottler(chainFactory, delays);
    }

    public static TaskThrottler create(TaskChainFactory chainFactory, DelayedTaskScheduler delays, int maxAttempts) {
        return new TaskThrottler(chainFactory, delays, maxAttempts);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public <T> TaskChain<T> throttle(Supplier<T> supplier, RateLimiter limiter) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(limiter, "limiter");

        return chainFactory.chain(throttleStage(supplier, limiter, 1));
    }

    private <T> CompletionStage<T> throttleStage(Supplier<T> supplier, RateLimiter limiter, int attempt) {
        return chainFactory
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

                        return delays.delayAsync(rejected.retryDelay())
                                .thenCompose(_ -> throttleStage(supplier, limiter, attempt + 1));
                    }
                    return CompletableFuture.failedStage(error);
                });
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
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
