package com.cotani.task.bucket;

import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Objects;

public final class TaskBucketFactory {

    private TaskBucketFactory() {}

    public static TaskBucket create(PaperTaskScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");

        return new DefaultTaskBucket(scheduler);
    }

    public static TaskBucket create(PaperTaskScheduler scheduler, long defaultCapacity, Duration defaultRefillPeriod) {
        Objects.requireNonNull(scheduler, "scheduler");

        return new DefaultTaskBucket(scheduler, defaultCapacity, defaultRefillPeriod);
    }
}
