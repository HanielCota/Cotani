package com.cotani.task.api;

import java.time.Duration;
import java.util.concurrent.Executor;

/** Dispatches work to the Paper/Folia global scheduler thread. */
public interface GlobalTaskScheduler {

    SchedulerTask global(Runnable runnable);

    SchedulerTask global(String name, Runnable runnable);

    SchedulerTask globalLater(Runnable runnable, Duration delay);

    SchedulerTask globalLater(String name, Runnable runnable, Duration delay);

    SchedulerTask globalTimer(Runnable runnable, Duration initialDelay, Duration period);

    Executor globalExecutor();
}
