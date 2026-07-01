package com.cotani.task.api;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface PaperTaskScheduler extends AutoCloseable {

    SchedulerTask async(Runnable runnable);

    SchedulerTask async(String name, Runnable runnable);

    SchedulerTask asyncLater(Runnable runnable, Duration delay);

    SchedulerTask asyncLater(String name, Runnable runnable, Duration delay);

    SchedulerTask asyncTimer(Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask global(Runnable runnable);

    SchedulerTask global(String name, Runnable runnable);

    SchedulerTask globalLater(Runnable runnable, Duration delay);

    SchedulerTask globalLater(String name, Runnable runnable, Duration delay);

    SchedulerTask globalTimer(Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask region(Location location, Runnable runnable);

    SchedulerTask region(String name, Location location, Runnable runnable);

    SchedulerTask regionLater(Location location, Runnable runnable, Duration delay);

    SchedulerTask regionLater(String name, Location location, Runnable runnable, Duration delay);

    SchedulerTask regionTimer(Location location, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask regionTimer(
            String name, Location location, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask entity(Entity entity, Runnable runnable);

    SchedulerTask entity(String name, Entity entity, Runnable runnable);

    SchedulerTask entityLater(Entity entity, Runnable runnable, Duration delay);

    SchedulerTask entityLater(String name, Entity entity, Runnable runnable, Duration delay);

    SchedulerTask entityTimer(Entity entity, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask entityTimer(String name, Entity entity, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask debounce(String name, Runnable runnable, Duration quietPeriod);

    SchedulerTask persistAndRun(String name, Duration delay, byte[] payload, Consumer<byte[]> executor);

    <T> TaskChain<T> supplyAsync(Supplier<T> supplier);

    <T> TaskChain<T> supplyAsync(String name, Supplier<T> supplier);

    <T> CompletableFuture<T> supply(ExecutionTarget target, String name, Supplier<T> supplier);

    @SuppressWarnings({"unchecked", "varargs"})
    default <T> TaskChain<List<T>> allOf(TaskChain<T>... chains) {
        return TaskChain.allOf(this, chains);
    }

    @SuppressWarnings({"unchecked", "varargs"})
    default <T> TaskChain<T> anyOf(TaskChain<T>... chains) {
        return TaskChain.anyOf(this, chains);
    }

    Executor asyncExecutor();

    Executor globalExecutor();

    Executor regionExecutor(Location location);

    Executor entityExecutor(Entity entity);

    com.cotani.task.metrics.TaskMetrics metrics();

    @Override
    void close();
}
