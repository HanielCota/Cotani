package com.cotani.task.api;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.Location;

/**
 * Dispatches work to the owner of a Paper/Folia region.
 *
 * <p>The UUID/chunk overloads avoid retaining a live {@link Location} across asynchronous flows.
 */
public interface RegionTaskScheduler {
    SchedulerTask region(Location location, Runnable runnable);

    SchedulerTask region(String name, Location location, Runnable runnable);

    SchedulerTask region(UUID worldId, int chunkX, int chunkZ, Runnable runnable);

    SchedulerTask region(String name, UUID worldId, int chunkX, int chunkZ, Runnable runnable);

    SchedulerTask regionLater(Location location, Runnable runnable, Duration delay);

    SchedulerTask regionLater(String name, Location location, Runnable runnable, Duration delay);

    SchedulerTask regionTimer(Location location, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask regionTimer(
            String name, Location location, Runnable runnable, Duration initialDelay, Duration period);

    Executor regionExecutor(Location location);

    Executor regionExecutor(UUID worldId, int chunkX, int chunkZ);
}
