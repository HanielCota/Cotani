package com.cotani.task.throttle;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import java.util.Objects;
import java.util.function.Supplier;

public final class TaskThrottler {

    private final PaperTaskScheduler scheduler;

    public TaskThrottler(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public <T> TaskChain<T> throttle(Supplier<T> supplier, RateLimiter limiter) {
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(limiter, "limiter");

        return scheduler.supplyAsync(() -> {
            try {
                limiter.acquire();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();

                throw new RuntimeException("Interrupted while acquiring rate limit permit", interrupted);
            }

            return supplier.get();
        });
    }
}
