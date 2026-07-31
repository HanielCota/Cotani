package com.cotani.task.impl.scheduler;

import com.cotani.task.api.SchedulerTask;
import java.time.Duration;

interface NamedAsyncTaskScheduler {
    SchedulerTask execute(String name, Runnable runnable);

    SchedulerTask schedule(String name, Runnable runnable, Duration delay);
}
