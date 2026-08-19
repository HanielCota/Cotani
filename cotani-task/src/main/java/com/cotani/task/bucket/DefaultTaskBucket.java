package com.cotani.task.bucket;

import com.cotani.api.InternalApi;
import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.DelayedTaskScheduler;
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

@InternalApi
public final class DefaultTaskBucket implements TaskBucket {
    private final AsyncTaskExecutor executor;
    private final DelayedTaskScheduler delays;
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final long defaultCapacity;
    private final Duration defaultRefillPeriod;

    private DefaultTaskBucket(PaperTaskScheduler scheduler) {
        this(scheduler, scheduler, 10, Duration.ofSeconds(1));
    }

    private DefaultTaskBucket(PaperTaskScheduler scheduler, long defaultCapacity, Duration defaultRefillPeriod) {
        this(scheduler, scheduler, defaultCapacity, defaultRefillPeriod);
    }

    private DefaultTaskBucket(AsyncTaskExecutor executor, DelayedTaskScheduler delays) {
        this(executor, delays, 10, Duration.ofSeconds(1));
    }

    private DefaultTaskBucket(
            AsyncTaskExecutor executor,
            DelayedTaskScheduler delays,
            long defaultCapacity,
            Duration defaultRefillPeriod) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.delays = Objects.requireNonNull(delays, "delays");

        if (defaultCapacity <= 0) {
            throw new IllegalArgumentException("defaultCapacity must be positive");
        }

        this.defaultCapacity = defaultCapacity;
        this.defaultRefillPeriod = Objects.requireNonNull(defaultRefillPeriod, "defaultRefillPeriod");
    }

    public static DefaultTaskBucket create(PaperTaskScheduler scheduler) {
        return new DefaultTaskBucket(scheduler);
    }

    public static DefaultTaskBucket create(
            PaperTaskScheduler scheduler, long defaultCapacity, Duration defaultRefillPeriod) {
        return new DefaultTaskBucket(scheduler, defaultCapacity, defaultRefillPeriod);
    }

    public static DefaultTaskBucket create(AsyncTaskExecutor executor, DelayedTaskScheduler delays) {
        return new DefaultTaskBucket(executor, delays);
    }

    public static DefaultTaskBucket create(
            AsyncTaskExecutor executor,
            DelayedTaskScheduler delays,
            long defaultCapacity,
            Duration defaultRefillPeriod) {
        return new DefaultTaskBucket(executor, delays, defaultCapacity, defaultRefillPeriod);
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

        SchedulerTask immediate = executor.async(taskName, () -> {
            SchedulerTask later = runThrottled(limiter, runnable, rescheduled);

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
                bucketName, ignored -> TokenBucketRateLimiter.create(defaultCapacity, defaultRefillPeriod));
    }

    private SchedulerTask runThrottled(
            RateLimiter limiter, Runnable runnable, AtomicReference<@Nullable SchedulerTask> holder) {
        if (limiter.tryAcquire()) {
            runnable.run();
            return SchedulerTask.noop();
        }

        Duration delay = limiter.retryDelay();

        if (delay.isZero() || delay.isNegative()) {
            delay = Duration.ofMillis(1);
        }
        return delays.asyncLater(
                () -> {
                    SchedulerTask next = runThrottled(limiter, runnable, holder);
                    holder.set(next);
                },
                delay);
    }

    @Override
    public void clear() {
        limiters.clear();
    }
}
