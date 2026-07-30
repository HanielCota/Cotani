package com.cotani.task.api;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.entity.Entity;

/**
 * Dispatches work to the thread that owns an entity.
 *
 * <p>UUID overloads resolve the entity at execution time. If the entity scheduler retires before
 * execution, value-producing dispatch completes exceptionally and runnable dispatch reports the
 * retirement through the configured exception handler.
 */
public interface EntityTaskScheduler {

    SchedulerTask entity(Entity entity, Runnable runnable);

    SchedulerTask entity(String name, Entity entity, Runnable runnable);

    SchedulerTask entity(UUID entityId, Runnable runnable);

    SchedulerTask entity(String name, UUID entityId, Runnable runnable);

    SchedulerTask entityLater(Entity entity, Runnable runnable, Duration delay);

    SchedulerTask entityLater(String name, Entity entity, Runnable runnable, Duration delay);

    SchedulerTask entityTimer(Entity entity, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask entityTimer(String name, Entity entity, Runnable runnable, Duration initialDelay, Duration period);

    Executor entityExecutor(Entity entity);

    Executor entityExecutor(UUID entityId);
}
