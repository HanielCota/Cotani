package com.cotani.task.api;

import com.cotani.task.metrics.TaskMetrics;
import com.cotani.task.persistence.PersistentTask;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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

    SchedulerTask region(UUID worldId, int chunkX, int chunkZ, Runnable runnable);

    SchedulerTask region(String name, UUID worldId, int chunkX, int chunkZ, Runnable runnable);

    SchedulerTask regionLater(Location location, Runnable runnable, Duration delay);

    SchedulerTask regionLater(String name, Location location, Runnable runnable, Duration delay);

    SchedulerTask regionTimer(Location location, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask regionTimer(
            String name, Location location, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask entity(Entity entity, Runnable runnable);

    SchedulerTask entity(String name, Entity entity, Runnable runnable);

    SchedulerTask entity(UUID entityId, Runnable runnable);

    SchedulerTask entity(String name, UUID entityId, Runnable runnable);

    SchedulerTask entityLater(Entity entity, Runnable runnable, Duration delay);

    SchedulerTask entityLater(String name, Entity entity, Runnable runnable, Duration delay);

    SchedulerTask entityTimer(Entity entity, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask entityTimer(String name, Entity entity, Runnable runnable, Duration initialDelay, Duration period);

    SchedulerTask debounce(String name, Runnable runnable, Duration quietPeriod);

    SchedulerTask persistAndRun(String name, Duration delay, byte[] payload, Consumer<byte[]> executor);

    CompletionStage<List<PersistentTask>> recoverPendingTasksAsync();

    <T> TaskChain<T> supplyAsync(Supplier<T> supplier);

    <T> TaskChain<T> supplyAsync(String name, Supplier<T> supplier);

    <T> CompletionStage<T> supply(ExecutionTarget target, String name, Supplier<T> supplier);

    <T> TaskChain<T> chain(CompletionStage<T> stage);

    @SuppressWarnings({"unchecked", "varargs"})
    default <T> TaskChain<List<T>> allOf(TaskChain<T>... chains) {
        return TaskChain.allOf(this, chains);
    }

    @SuppressWarnings({"unchecked", "varargs"})
    default <T> TaskChain<T> anyOf(TaskChain<T>... chains) {
        return TaskChain.anyOf(this, chains);
    }

    default CompletionStage<Void> delayAsync(Duration duration) {
        Objects.requireNonNull(duration, "duration");

        CompletableFuture<Void> future = new CompletableFuture<>();
        SchedulerTask pending = asyncLater("scheduler-delay", () -> future.complete(null), duration);
        var _ = future.whenCompleteAsync(
                (_, _) -> pending.cancel(),
                CompletableFuture.delayedExecutor(duration.toMillis(), TimeUnit.MILLISECONDS));
        return future;
    }

    Executor asyncExecutor();

    Executor globalExecutor();

    Executor regionExecutor(Location location);

    Executor regionExecutor(UUID worldId, int chunkX, int chunkZ);

    Executor entityExecutor(Entity entity);

    Executor entityExecutor(UUID entityId);

    TaskMetrics metrics();

    TaskExceptionHandler exceptionHandler();

    @Override
    void close();
}
