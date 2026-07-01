package com.cotani.task.bucket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.throttle.RateLimiter;
import org.junit.jupiter.api.Test;

class DefaultTaskBucketTest {

    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
    private final TaskBucket bucket = TaskBucketFactory.create(scheduler);

    @Test
    void submitReturnsTask() {
        when(scheduler.async(any(String.class), any(Runnable.class))).thenReturn(mock(SchedulerTask.class));

        SchedulerTask task = bucket.submit("queries", () -> {});

        assertNotNull(task);
    }

    @Test
    void limiterForReturnsSameLimiterForSameBucket() {
        RateLimiter first = bucket.limiterFor("queries");
        RateLimiter second = bucket.limiterFor("queries");

        assertSame(first, second);
    }

    @Test
    void limiterForReturnsDifferentLimiterForDifferentBuckets() {
        RateLimiter first = bucket.limiterFor("queries");
        RateLimiter second = bucket.limiterFor("http");

        assertNotNull(first);
        assertNotNull(second);
    }
}
