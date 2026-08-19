package com.cotani.task.bucket;

import com.cotani.task.api.SchedulerTask;
import com.cotani.task.throttle.RateLimiter;

public interface TaskBucket extends AutoCloseable {
    SchedulerTask submit(String bucketName, Runnable runnable);

    SchedulerTask submit(String bucketName, String taskName, Runnable runnable);

    RateLimiter limiterFor(String bucketName);

    void clear();

    @Override
    default void close() {
        clear();
    }
}
