package com.cotani.task.bucket;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.throttle.RateLimiter;
import com.cotani.task.throttle.TokenBucketRateLimiter;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class DefaultTaskBucket implements TaskBucket {

    private final PaperTaskScheduler scheduler;
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final long defaultCapacity;
    private final Duration defaultRefillPeriod;

    public DefaultTaskBucket(PaperTaskScheduler scheduler) {
        this(scheduler, 10, Duration.ofSeconds(1));
    }

    public DefaultTaskBucket(PaperTaskScheduler scheduler, long defaultCapacity, Duration defaultRefillPeriod) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");

        if (defaultCapacity <= 0) {
            throw new IllegalArgumentException("defaultCapacity must be positive");
        }

        this.defaultCapacity = defaultCapacity;
        this.defaultRefillPeriod = Objects.requireNonNull(defaultRefillPeriod, "defaultRefillPeriod");
    }

    @Override
    public SchedulerTask submit(String bucketName, Runnable runnable) {
        return submit(bucketName, bucketName + "-task", runnable);
    }

    @Override
    public SchedulerTask submit(String bucketName, String taskName, Runnable runnable) {
        Objects.requireNonNull(bucketName, "bucketName");
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(runnable, "runnable");

        RateLimiter limiter = limiterFor(bucketName);
        AtomicReference<@Nullable SchedulerTask> rescheduled = new AtomicReference<>();

        SchedulerTask immediate = scheduler.async(taskName, () -> {
            SchedulerTask later = runThrottled(limiter, runnable);

            if (later != null) {
                rescheduled.set(later);
            }
        });

        return new CompositeSchedulerTask(immediate, rescheduled);
    }

    @Override
    public RateLimiter limiterFor(String bucketName) {
        Objects.requireNonNull(bucketName, "bucketName");

        return limiters.computeIfAbsent(
                bucketName, ignored -> new TokenBucketRateLimiter(defaultCapacity, defaultRefillPeriod));
    }

    private SchedulerTask runThrottled(RateLimiter limiter, Runnable runnable) {
        if (limiter.tryAcquire()) {
            runnable.run();
            return SchedulerTask.noop();
        }

        Duration delay = limiter.retryDelay();
        if (delay.isZero() || delay.isNegative()) {
            delay = Duration.ofMillis(1);
        }
        return scheduler.asyncLater(runnable, delay);
    }
}
