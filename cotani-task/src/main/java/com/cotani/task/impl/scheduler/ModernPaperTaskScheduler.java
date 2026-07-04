package com.cotani.task.impl.scheduler;

import com.cotani.task.api.*;
import com.cotani.task.impl.chain.DefaultTaskChain;
import com.cotani.task.impl.dispatch.TaskDispatcher;
import com.cotani.task.impl.dispatch.TaskErrorReporter;
import com.cotani.task.impl.dispatch.TaskRunner;
import com.cotani.task.impl.task.LazySchedulerTask;
import com.cotani.task.metrics.TaskMetrics;
import com.cotani.task.persistence.NoopPersistentTaskStore;
import com.cotani.task.persistence.PersistentTask;
import com.cotani.task.persistence.PersistentTaskStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class ModernPaperTaskScheduler implements PaperTaskScheduler {

    private final PlatformScheduler platformScheduler;
    private final SchedulerOptions options;
    private final TaskRunner taskRunner;
    private final TaskErrorReporter taskErrorReporter;
    private final TaskExceptionHandler exceptionHandler;
    private final TaskDispatcher taskDispatcher;
    private final TaskMetrics metrics;
    private final PersistentTaskStore persistentTaskStore;
    private final Map<String, SchedulerTask> pendingDebounces = new ConcurrentHashMap<>();

    public ModernPaperTaskScheduler(
            PlatformScheduler platformScheduler,
            TaskExceptionHandler exceptionHandler,
            SchedulerOptions options,
            TaskMetrics metrics) {
        this(platformScheduler, exceptionHandler, options, metrics, new NoopPersistentTaskStore());
    }

    public ModernPaperTaskScheduler(
            PlatformScheduler platformScheduler,
            TaskExceptionHandler exceptionHandler,
            SchedulerOptions options,
            TaskMetrics metrics,
            PersistentTaskStore persistentTaskStore) {
        this.platformScheduler = Objects.requireNonNull(platformScheduler, "platformScheduler");
        this.options = Objects.requireNonNull(options, "options");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.persistentTaskStore = Objects.requireNonNull(persistentTaskStore, "persistentTaskStore");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        this.taskRunner = new TaskRunner(exceptionHandler, metrics);
        this.taskErrorReporter = new TaskErrorReporter(exceptionHandler);
        this.taskDispatcher = new TaskDispatcher(platformScheduler, taskRunner);
    }

    @Override
    public SchedulerTask async(Runnable runnable) {
        return async("async-task", runnable);
    }

    @Override
    public SchedulerTask async(String name, Runnable runnable) {
        var metadata = metadata(name, ExecutionTarget.async());

        return platformScheduler.runAsync(metadata, taskRunner.wrap(metadata, runnable));
    }

    @Override
    public SchedulerTask asyncLater(Runnable runnable, Duration delay) {
        return asyncLater("async-later-task", runnable, delay);
    }

    @Override
    public SchedulerTask asyncLater(String name, Runnable runnable, Duration delay) {
        var metadata = metadata(name, ExecutionTarget.async());

        return platformScheduler.runAsyncLater(metadata, taskRunner.wrap(metadata, runnable), delay);
    }

    @Override
    public SchedulerTask asyncTimer(Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadata("async-timer-task", ExecutionTarget.async());

        return platformScheduler.runAsyncTimer(metadata, taskRunner.wrap(metadata, runnable), initialDelay, period);
    }

    @Override
    public SchedulerTask global(Runnable runnable) {
        return global("global-task", runnable);
    }

    @Override
    public SchedulerTask global(String name, Runnable runnable) {
        var metadata = metadata(name, ExecutionTarget.global());

        return platformScheduler.runGlobal(metadata, taskRunner.wrap(metadata, runnable));
    }

    @Override
    public SchedulerTask globalLater(Runnable runnable, Duration delay) {
        return globalLater("global-later-task", runnable, delay);
    }

    @Override
    public SchedulerTask globalLater(String name, Runnable runnable, Duration delay) {
        var metadata = metadata(name, ExecutionTarget.global());

        return platformScheduler.runGlobalLater(metadata, taskRunner.wrap(metadata, runnable), delay);
    }

    @Override
    public SchedulerTask globalTimer(Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadata("global-timer-task", ExecutionTarget.global());

        return platformScheduler.runGlobalTimer(metadata, taskRunner.wrap(metadata, runnable), initialDelay, period);
    }

    @Override
    public SchedulerTask region(Location location, Runnable runnable) {
        return region("region-task", location, runnable);
    }

    @Override
    public SchedulerTask region(String name, Location location, Runnable runnable) {
        var metadata = metadata(name, ExecutionTarget.region(location));

        return platformScheduler.runRegion(metadata, location, taskRunner.wrap(metadata, runnable));
    }

    @Override
    public SchedulerTask region(UUID worldId, int chunkX, int chunkZ, Runnable runnable) {
        return region("region-task", worldId, chunkX, chunkZ, runnable);
    }

    @Override
    public SchedulerTask region(String name, UUID worldId, int chunkX, int chunkZ, Runnable runnable) {
        var target = ExecutionTarget.region(worldId, chunkX, chunkZ);
        var metadata = metadata(name, target);
        var location = ((ExecutionTarget.Region) target).location();

        return platformScheduler.runRegion(metadata, location, taskRunner.wrap(metadata, runnable));
    }

    @Override
    public SchedulerTask regionLater(Location location, Runnable runnable, Duration delay) {
        return regionLater("region-later-task", location, runnable, delay);
    }

    @Override
    public SchedulerTask regionLater(String name, Location location, Runnable runnable, Duration delay) {
        var metadata = metadata(name, ExecutionTarget.region(location));

        return platformScheduler.runRegionLater(metadata, location, taskRunner.wrap(metadata, runnable), delay);
    }

    @Override
    public SchedulerTask regionTimer(Location location, Runnable runnable, Duration initialDelay, Duration period) {
        return regionTimer("region-timer-task", location, runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask regionTimer(
            String name, Location location, Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadata(name, ExecutionTarget.region(location));

        return platformScheduler.runRegionTimer(
                metadata, location, taskRunner.wrap(metadata, runnable), initialDelay, period);
    }

    @Override
    public SchedulerTask entity(Entity entity, Runnable runnable) {
        return entity("entity-task", entity, runnable);
    }

    @Override
    public SchedulerTask entity(String name, Entity entity, Runnable runnable) {
        var metadata = metadata(name, ExecutionTarget.entity(entity));

        return platformScheduler.runEntity(
                metadata, entity, taskRunner.wrap(metadata, runnable), () -> taskErrorReporter.handleRetired(metadata));
    }

    @Override
    public SchedulerTask entity(UUID entityId, Runnable runnable) {
        return entity("entity-task", entityId, runnable);
    }

    @Override
    public SchedulerTask entity(String name, UUID entityId, Runnable runnable) {
        var target = ExecutionTarget.entity(entityId);
        var metadata = metadata(name, target);
        var entity = ((ExecutionTarget.EntityTarget) target).entity();

        return platformScheduler.runEntity(
                metadata, entity, taskRunner.wrap(metadata, runnable), () -> taskErrorReporter.handleRetired(metadata));
    }

    @Override
    public SchedulerTask entityLater(Entity entity, Runnable runnable, Duration delay) {
        return entityLater("entity-later-task", entity, runnable, delay);
    }

    @Override
    public SchedulerTask entityLater(String name, Entity entity, Runnable runnable, Duration delay) {
        var metadata = metadata(name, ExecutionTarget.entity(entity));

        return platformScheduler.runEntityLater(
                metadata,
                entity,
                taskRunner.wrap(metadata, runnable),
                () -> taskErrorReporter.handleRetired(metadata),
                delay);
    }

    @Override
    public SchedulerTask entityTimer(Entity entity, Runnable runnable, Duration initialDelay, Duration period) {
        return entityTimer("entity-timer-task", entity, runnable, initialDelay, period);
    }

    @Override
    public SchedulerTask entityTimer(
            String name, Entity entity, Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadata(name, ExecutionTarget.entity(entity));

        return platformScheduler.runEntityTimer(
                metadata,
                entity,
                taskRunner.wrap(metadata, runnable),
                () -> taskErrorReporter.handleRetired(metadata),
                initialDelay,
                period);
    }

    @Override
    public SchedulerTask debounce(String name, Runnable runnable, Duration quietPeriod) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(quietPeriod, "quietPeriod");

        final SchedulerTask[] taskHolder = new SchedulerTask[1];
        SchedulerTask previous = pendingDebounces.remove(name);

        var metadata = metadata("debounce-" + name, ExecutionTarget.async());
        SchedulerTask task = platformScheduler.runAsyncLater(
                metadata,
                taskRunner.wrap(metadata, () -> {
                    pendingDebounces.remove(name, taskHolder[0]);
                    runnable.run();
                }),
                quietPeriod);
        taskHolder[0] = task;

        SchedulerTask stale = pendingDebounces.put(name, task);
        if (stale != null && !stale.equals(previous)) {
            stale.cancel();
        }
        if (previous != null) {
            previous.cancel();
        }

        return task;
    }

    @Override
    public SchedulerTask persistAndRun(String name, Duration delay, byte[] payload, Consumer<byte[]> executor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(executor, "executor");

        var task = new PersistentTask(UUID.randomUUID(), name, Instant.now(), delay, payload);
        var lazyTask = new LazySchedulerTask();

        SchedulerTask saveTask = async("persist-save-" + name, () -> {
            persistentTaskStore.save(task);

            if (lazyTask.cancelled()) {
                return;
            }

            SchedulerTask execTask = asyncLater(
                    "persist-run-" + name,
                    () -> {
                        try {
                            executor.accept(payload);
                        } finally {
                            persistentTaskStore.markCompleted(task);
                        }
                    },
                    delay);

            lazyTask.setDelegate(execTask);
        });

        lazyTask.setSetupTask(saveTask);

        return lazyTask;
    }

    @Override
    public CompletionStage<List<PersistentTask>> recoverPendingTasksAsync() {
        return supplyAsync("recover-pending", persistentTaskStore::loadPending).toCompletionStage();
    }

    @Override
    public <T> TaskChain<T> supplyAsync(Supplier<T> supplier) {
        return supplyAsync("supply-async-task", supplier);
    }

    @Override
    public <T> TaskChain<T> supplyAsync(String name, Supplier<T> supplier) {
        var future = supply(ExecutionTarget.async(), name, supplier);

        return new DefaultTaskChain<>(future, this);
    }

    @Override
    public <T> CompletableFuture<T> supply(ExecutionTarget target, String name, Supplier<T> supplier) {
        var future = new CompletableFuture<T>();
        var metadata = metadata(name, target);
        Runnable runnable = () -> taskRunner.complete(metadata, supplier, future);

        taskDispatcher.dispatch(target, metadata, runnable, future);

        return future;
    }

    @Override
    public <T> TaskChain<T> chain(CompletionStage<T> stage) {
        return new DefaultTaskChain<>(stage.toCompletableFuture(), this);
    }

    @Override
    public Executor asyncExecutor() {
        return command -> async("executor-async", command);
    }

    @Override
    public Executor globalExecutor() {
        return command -> global("executor-global", command);
    }

    @Override
    public Executor regionExecutor(Location location) {
        return command -> region("executor-region", location, command);
    }

    @Override
    public Executor regionExecutor(UUID worldId, int chunkX, int chunkZ) {
        return command -> region("executor-region", worldId, chunkX, chunkZ, command);
    }

    @Override
    public Executor entityExecutor(Entity entity) {
        return command -> entity("executor-entity", entity, command);
    }

    @Override
    public Executor entityExecutor(UUID entityId) {
        return command -> entity("executor-entity", entityId, command);
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
    public void close() {
        if (options.cancelPaperTasksOnClose()) {
            platformScheduler.cancelOwnedTasks();
        }

        if (platformScheduler instanceof AutoCloseable closeable) {
            taskErrorReporter.closePlatform(metadata("scheduler-close", ExecutionTarget.async()), closeable);
        }
    }

    private TaskMetadata metadata(String name, ExecutionTarget target) {
        return TaskMetadata.named(name, target);
    }
}
