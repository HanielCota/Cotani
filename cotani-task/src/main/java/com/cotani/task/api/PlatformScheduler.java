package com.cotani.task.api;

import java.time.Duration;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@SuppressWarnings("unused")
public interface PlatformScheduler {

    SchedulerTask runAsync(TaskMetadata metadata, Runnable runnable);

    SchedulerTask runAsyncLater(TaskMetadata metadata, Runnable runnable, Duration delay);

    SchedulerTask runAsyncTimer(TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask runGlobal(TaskMetadata metadata, Runnable runnable);

    SchedulerTask runGlobalLater(TaskMetadata metadata, Runnable runnable, Duration delay);

    SchedulerTask runGlobalTimer(TaskMetadata metadata, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask runRegion(TaskMetadata metadata, Location location, Runnable runnable);

    SchedulerTask runRegionLater(TaskMetadata metadata, Location location, Runnable runnable, Duration delay);

    SchedulerTask runRegionTimer(
            TaskMetadata metadata, Location location, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask runEntity(TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired);

    SchedulerTask runEntityLater(
            TaskMetadata metadata, Entity entity, Runnable runnable, Runnable retired, Duration delay);

    SchedulerTask runEntityTimer(
            TaskMetadata metadata,
            Entity entity,
            Runnable runnable,
            Runnable retired,
            Duration initialDelay,
            Duration period);

    void cancelOwnedTasks();
}
