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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@com.cotani.api.InternalApi
public final class ModernPaperTaskScheduler implements PaperTaskScheduler {

    private final PlatformScheduler platformScheduler;
    private final SchedulerOptions options;
    private final TaskRunner taskRunner;
    private final TaskErrorReporter taskErrorReporter;
    private final TaskExceptionHandler exceptionHandler;
    private final TaskDispatcher taskDispatcher;
    private final TaskMetrics metrics;
    private final PersistentTaskStore persistentTaskStore;
    private final Map<String, DebounceTask> pendingDebounces = new ConcurrentHashMap<>();
    private final Executor cachedAsyncExecutor;
    private final Executor cachedGlobalExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Void>> closeFuture = new AtomicReference<>();

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
        this.cachedAsyncExecutor = command -> async("executor-async", command);
        this.cachedGlobalExecutor = command -> global("executor-global", command);
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

        return platformScheduler.runRegion(metadata, worldId, chunkX, chunkZ, taskRunner.wrap(metadata, runnable));
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

        return platformScheduler.runEntity(
                metadata,
                entityId,
                taskRunner.wrap(metadata, runnable),
                () -> taskErrorReporter.handleRetired(metadata));
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

        var metadata = metadata("debounce-" + name, ExecutionTarget.async());
        var debounce = new DebounceTask(name, runnable);
        pendingDebounces.compute(name, (_, current) -> {
            if (current != null) {
                current.supersede();
            }
            return debounce;
        });

        try {
            SchedulerTask scheduled = platformScheduler.runAsyncLater(
                    metadata, taskRunner.wrap(metadata, debounce::executeIfCurrent), quietPeriod);
            debounce.attach(scheduled);
            return debounce;
        } catch (Throwable failure) {
            pendingDebounces.remove(name, debounce);
            debounce.cancel();
            throw failure;
        }
    }

    @Override
    public SchedulerTask persistAndRun(String name, Duration delay, byte[] payload, Consumer<byte[]> executor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(executor, "executor");

        var task = new PersistentTask(UUID.randomUUID(), name, Instant.now(), delay, payload);
        var lazyTask = new LazySchedulerTask();
        var persistentHandle = new PersistentSchedulerTask(task, lazyTask);

        SchedulerTask saveTask = async("persist-save-" + name, () -> {
            SchedulerTask execTask;

            try {
                persistentTaskStore.save(task);
            } catch (Throwable failure) {
                lazyTask.failSetup(failure);

                return;
            }
            persistentHandle.markPersisted();

            if (lazyTask.cancelled()) {
                persistentHandle.completePersistence();
                lazyTask.completeSetup(SchedulerTask.noop());

                return;
            }

            execTask = asyncLater(
                    "persist-run-" + name,
                    () -> {
                        try {
                            executor.accept(task.payload());
                        } finally {
                            persistentHandle.completePersistence();
                        }
                    },
                    delay);

            lazyTask.setDelegate(execTask);
            lazyTask.completeSetup(execTask);
        });

        lazyTask.setSetupTask(saveTask);

        return persistentHandle;
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
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(supplier, "supplier");
        Supplier<CompletableFuture<T>> factory = () -> supply(ExecutionTarget.async(), name, supplier);
        var future = factory.get();

        return new DefaultTaskChain<>(future, this, factory);
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
        return cachedAsyncExecutor;
    }

    @Override
    public Executor globalExecutor() {
        return cachedGlobalExecutor;
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
    public CompletionStage<Void> closeAsync() {
        var existing = closeFuture.get();
        if (existing != null) {
            return existing;
        }

        var promise = new CompletableFuture<Void>();
        if (!closeFuture.compareAndSet(null, promise)) {
            return Objects.requireNonNull(closeFuture.get(), "closeFuture");
        }

        beginClose();
        CompletionStage<Void> platformClose;
        if (platformScheduler instanceof PaperPlatformScheduler paperScheduler) {
            platformClose = paperScheduler.closeAsync();
        } else if (platformScheduler instanceof AutoCloseable closeable) {
            platformClose = closeOnDedicatedThread(closeable);
        } else {
            platformClose = CompletableFuture.completedFuture(null);
        }
        var _ = platformClose.whenComplete((_, failure) -> {
            if (failure == null) {
                promise.complete(null);
            } else {
                promise.completeExceptionally(failure);
            }
        });
        return promise;
    }

    @Override
    public void close() {
        if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "PaperTaskScheduler.close() blocks; use closeAsync() on the server thread.");
        }
        var promise = new CompletableFuture<Void>();
        if (!closeFuture.compareAndSet(null, promise)) {
            beginClose();
            return;
        }
        beginClose();
        if (platformScheduler instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                promise.complete(null);
            } catch (RuntimeException failure) {
                promise.completeExceptionally(failure);
                throw failure;
            } catch (Exception failure) {
                promise.completeExceptionally(failure);
                throw new IllegalStateException("Could not close scheduler resources", failure);
            }
        } else {
            promise.complete(null);
        }
    }

    private void beginClose() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pendingDebounces.values().forEach(SchedulerTask::cancel);
        pendingDebounces.clear();
        if (options.cancelPaperTasksOnClose()) {
            platformScheduler.cancelOwnedTasks();
        }
    }

    private static CompletionStage<Void> closeOnDedicatedThread(AutoCloseable closeable) {
        var promise = new CompletableFuture<Void>();
        Thread.ofPlatform().daemon(true).name("cotani-task-platform-shutdown").start(() -> {
            try {
                closeable.close();
                promise.complete(null);
            } catch (Throwable failure) {
                promise.completeExceptionally(failure);
            }
        });
        return promise;
    }

    private TaskMetadata metadata(String name, ExecutionTarget target) {
        if (closed.get()) {
            throw new java.util.concurrent.RejectedExecutionException("PaperTaskScheduler is closed.");
        }
        return TaskMetadata.named(name, target);
    }

    private final class DebounceTask implements SchedulerTask {

        private final String name;
        private final Runnable runnable;
        private final AtomicReference<SchedulerTask> delegate = new AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean executed =
                new java.util.concurrent.atomic.AtomicBoolean();

        private DebounceTask(String name, Runnable runnable) {
            this.name = name;
            this.runnable = runnable;
        }

        private void attach(SchedulerTask task) {
            delegate.set(Objects.requireNonNull(task, "task"));
            if (cancelled.get() || executed.get()) {
                task.cancel();
            }
        }

        private void executeIfCurrent() {
            if (!pendingDebounces.remove(name, this) || cancelled.get() || !executed.compareAndSet(false, true)) {
                return;
            }
            runnable.run();
        }

        @Override
        public boolean cancel() {
            boolean changed = supersede();
            pendingDebounces.remove(name, this);
            return changed;
        }

        private boolean supersede() {
            boolean changed = cancelled.compareAndSet(false, true);
            SchedulerTask task = delegate.get();
            if (task != null) {
                task.cancel();
            }
            return changed;
        }

        @Override
        public boolean cancelled() {
            SchedulerTask task = delegate.get();
            return cancelled.get() || (task != null && task.cancelled());
        }
    }

    private final class PersistentSchedulerTask implements SchedulerTask {

        private final PersistentTask persistentTask;
        private final LazySchedulerTask delegate;
        private final java.util.concurrent.atomic.AtomicBoolean persisted =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicBoolean completionScheduled =
                new java.util.concurrent.atomic.AtomicBoolean();
        private boolean persistenceCompleted;

        private PersistentSchedulerTask(PersistentTask persistentTask, LazySchedulerTask delegate) {
            this.persistentTask = persistentTask;
            this.delegate = delegate;
        }

        private void markPersisted() {
            persisted.set(true);
        }

        private synchronized void completePersistence() {
            if (persistenceCompleted) {
                return;
            }
            persistentTaskStore.markCompleted(persistentTask);
            persistenceCompleted = true;
        }

        private void scheduleCancellationPersistence() {
            if (!persisted.get() || !completionScheduled.compareAndSet(false, true)) {
                return;
            }
            try {
                async("persist-cancel-" + persistentTask.taskName(), () -> {
                    try {
                        completePersistence();
                    } finally {
                        completionScheduled.set(false);
                    }
                });
            } catch (RuntimeException schedulingFailure) {
                completionScheduled.set(false);
                throw schedulingFailure;
            }
        }

        @Override
        public boolean cancel() {
            boolean changed = cancelled.compareAndSet(false, true);
            delegate.cancel();
            scheduleCancellationPersistence();
            return changed;
        }

        @Override
        public boolean cancelled() {
            return cancelled.get() || delegate.cancelled();
        }
    }
}
