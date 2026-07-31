package com.cotani.task.impl.scheduler;

import com.cotani.api.InternalApi;
import com.cotani.task.api.*;
import com.cotani.task.impl.dispatch.TaskErrorReporter;
import com.cotani.task.impl.dispatch.TaskRunner;
import com.cotani.task.metrics.TaskMetrics;
import com.cotani.task.persistence.NoopPersistentTaskStore;
import com.cotani.task.persistence.PersistentTask;
import com.cotani.task.persistence.PersistentTaskStore;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@InternalApi
public final class ModernPaperTaskScheduler implements PaperTaskScheduler {
    private final TaskExceptionHandler exceptionHandler;
    private final TaskMetrics metrics;
    private final TargetTaskDispatcher dispatcher;
    private final DebounceCoordinator debounceCoordinator;
    private final PersistentTaskCoordinator persistentTaskCoordinator;
    private final SchedulerTaskChainFactory chainFactory;
    private final SchedulerLifecycleCoordinator lifecycle;

    private ModernPaperTaskScheduler(
            PlatformScheduler platformScheduler,
            TaskExceptionHandler exceptionHandler,
            SchedulerOptions options,
            TaskMetrics metrics) {
        this(platformScheduler, exceptionHandler, options, metrics, new NoopPersistentTaskStore());
    }

    private ModernPaperTaskScheduler(
            PlatformScheduler platformScheduler,
            TaskExceptionHandler exceptionHandler,
            SchedulerOptions options,
            TaskMetrics metrics,
            PersistentTaskStore persistentTaskStore) {
        var validatedPlatform = Objects.requireNonNull(platformScheduler, "platformScheduler");
        var validatedOptions = Objects.requireNonNull(options, "options");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        this.lifecycle =
                new SchedulerLifecycleCoordinator(validatedPlatform, validatedOptions.cancelPaperTasksOnClose());
        var taskRunner = TaskRunner.create(exceptionHandler, metrics);
        var taskErrorReporter = TaskErrorReporter.create(exceptionHandler);
        this.dispatcher =
                new TargetTaskDispatcher(validatedPlatform, taskRunner, taskErrorReporter, lifecycle::metadata);
        this.debounceCoordinator = new DebounceCoordinator(dispatcher);
        this.persistentTaskCoordinator = new PersistentTaskCoordinator(persistentTaskStore, dispatcher);
        this.chainFactory = new SchedulerTaskChainFactory(this, dispatcher);
    }

    public static ModernPaperTaskScheduler create(
            PlatformScheduler platformScheduler,
            TaskExceptionHandler exceptionHandler,
            SchedulerOptions options,
            TaskMetrics metrics) {
        return new ModernPaperTaskScheduler(platformScheduler, exceptionHandler, options, metrics);
    }

    public static ModernPaperTaskScheduler create(
            PlatformScheduler platformScheduler,
            TaskExceptionHandler exceptionHandler,
            SchedulerOptions options,
            TaskMetrics metrics,
            PersistentTaskStore persistentTaskStore) {
        return new ModernPaperTaskScheduler(platformScheduler, exceptionHandler, options, metrics, persistentTaskStore);
    }

    @Override
    public SchedulerTask async(Runnable runnable) {
        return async("async-task", runnable);
    }

    @Override
    public SchedulerTask async(String name, Runnable runnable) {
        return dispatcher.async(name, runnable);
    }

    @Override
    public SchedulerTask asyncLater(Runnable runnable, Duration delay) {
        return asyncLater("async-later-task", runnable, delay);
    }

    @Override
    public SchedulerTask asyncLater(String name, Runnable runnable, Duration delay) {
        return dispatcher.asyncLater(name, runnable, delay);
    }

    @Override
    public SchedulerTask asyncTimer(Runnable runnable, Duration initialDelay, Duration period) {
        return dispatcher.asyncTimer("async-timer-task", runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask global(Runnable runnable) {
        return global("global-task", runnable);
    }

    @Override
    public SchedulerTask global(String name, Runnable runnable) {
        return dispatcher.global(name, runnable);
    }

    @Override
    public SchedulerTask globalLater(Runnable runnable, Duration delay) {
        return globalLater("global-later-task", runnable, delay);
    }

    @Override
    public SchedulerTask globalLater(String name, Runnable runnable, Duration delay) {
        return dispatcher.globalLater(name, runnable, delay);
    }

    @Override
    public SchedulerTask globalTimer(Runnable runnable, Duration initialDelay, Duration period) {
        return dispatcher.globalTimer("global-timer-task", runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask region(Location location, Runnable runnable) {
        return region("region-task", location, runnable);
    }

    @Override
    public SchedulerTask region(String name, Location location, Runnable runnable) {
        return dispatcher.region(name, location, runnable);
    }

    @Override
    public SchedulerTask region(UUID worldId, int chunkX, int chunkZ, Runnable runnable) {
        return region("region-task", worldId, chunkX, chunkZ, runnable);
    }

    @Override
    public SchedulerTask region(String name, UUID worldId, int chunkX, int chunkZ, Runnable runnable) {
        return dispatcher.region(name, worldId, chunkX, chunkZ, runnable);
    }

    @Override
    public SchedulerTask regionLater(Location location, Runnable runnable, Duration delay) {
        return regionLater("region-later-task", location, runnable, delay);
    }

    @Override
    public SchedulerTask regionLater(String name, Location location, Runnable runnable, Duration delay) {
        return dispatcher.regionLater(name, location, runnable, delay);
    }

    @Override
    public SchedulerTask regionTimer(Location location, Runnable runnable, Duration initialDelay, Duration period) {
        return regionTimer("region-timer-task", location, runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask regionTimer(
            String name, Location location, Runnable runnable, Duration initialDelay, Duration period) {
        return dispatcher.regionTimer(name, location, runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask entity(Entity entity, Runnable runnable) {
        return entity("entity-task", entity, runnable);
    }

    @Override
    public SchedulerTask entity(String name, Entity entity, Runnable runnable) {
        return dispatcher.entity(name, entity, runnable);
    }

    @Override
    public SchedulerTask entity(UUID entityId, Runnable runnable) {
        return entity("entity-task", entityId, runnable);
    }

    @Override
    public SchedulerTask entity(String name, UUID entityId, Runnable runnable) {
        return dispatcher.entity(name, entityId, runnable);
    }

    @Override
    public SchedulerTask entityLater(Entity entity, Runnable runnable, Duration delay) {
        return entityLater("entity-later-task", entity, runnable, delay);
    }

    @Override
    public SchedulerTask entityLater(String name, Entity entity, Runnable runnable, Duration delay) {
        return dispatcher.entityLater(name, entity, runnable, delay);
    }

    @Override
    public SchedulerTask entityTimer(Entity entity, Runnable runnable, Duration initialDelay, Duration period) {
        return entityTimer("entity-timer-task", entity, runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask entityTimer(
            String name, Entity entity, Runnable runnable, Duration initialDelay, Duration period) {
        return dispatcher.entityTimer(name, entity, runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask debounce(String name, Runnable runnable, Duration quietPeriod) {
        return debounceCoordinator.debounce(name, runnable, quietPeriod);
    }

    @Override
    public SchedulerTask persistAndRun(String name, Duration delay, byte[] payload, Consumer<byte[]> executor) {
        return persistentTaskCoordinator.persistAndRun(name, delay, payload, executor);
    }

    @Override
    public CompletionStage<List<PersistentTask>> recoverPendingTasksAsync() {
        return supplyAsync("recover-pending", persistentTaskCoordinator::loadPending)
                .toCompletionStage();
    }

    @Override
    public <T> TaskChain<T> supplyAsync(Supplier<T> supplier) {
        return supplyAsync("supply-async-task", supplier);
    }

    @Override
    public <T> TaskChain<T> supplyAsync(String name, Supplier<T> supplier) {
        return chainFactory.supplyAsync(name, supplier);
    }

    @Override
    public <T> CompletableFuture<T> supply(ExecutionTarget target, String name, Supplier<T> supplier) {
        return dispatcher.supply(target, name, supplier);
    }

    @Override
    public <T> TaskChain<T> chain(CompletionStage<T> stage) {
        return chainFactory.chain(stage);
    }

    @Override
    public Executor asyncExecutor() {
        return dispatcher.asyncExecutor();
    }

    @Override
    public Executor globalExecutor() {
        return dispatcher.globalExecutor();
    }

    @Override
    public Executor regionExecutor(Location location) {
        return dispatcher.regionExecutor(location);
    }

    @Override
    public Executor regionExecutor(UUID worldId, int chunkX, int chunkZ) {
        return dispatcher.regionExecutor(worldId, chunkX, chunkZ);
    }

    @Override
    public Executor entityExecutor(Entity entity) {
        return dispatcher.entityExecutor(entity);
    }

    @Override
    public Executor entityExecutor(UUID entityId) {
        return dispatcher.entityExecutor(entityId);
    }

    @Override
    public TaskMetrics metrics() {
        return metrics;
    }

    @Override
    public TaskExceptionHandler exceptionHandler() {
        return exceptionHandler;
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return lifecycle.closeAsync(debounceCoordinator::cancelAll);
    }

    @Override
    public void close() {
        lifecycle.close(debounceCoordinator::cancelAll);
    }
}
