package com.cotani.task.impl.scheduler;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.impl.dispatch.TaskDispatcher;
import com.cotani.task.impl.dispatch.TaskErrorReporter;
import com.cotani.task.impl.dispatch.TaskRunner;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

final class TargetTaskDispatcher implements NamedAsyncTaskScheduler {

    private final PlatformScheduler platformScheduler;
    private final TaskRunner taskRunner;
    private final TaskErrorReporter taskErrorReporter;
    private final TaskDispatcher taskDispatcher;
    private final TaskMetadataFactory metadataFactory;
    private final Executor asyncExecutor = command -> async("executor-async", command);
    private final Executor globalExecutor = command -> global("executor-global", command);

    TargetTaskDispatcher(
            PlatformScheduler platformScheduler,
            TaskRunner taskRunner,
            TaskErrorReporter taskErrorReporter,
            TaskMetadataFactory metadataFactory) {
        this.platformScheduler = Objects.requireNonNull(platformScheduler, "platformScheduler");
        this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
        this.taskErrorReporter = Objects.requireNonNull(taskErrorReporter, "taskErrorReporter");
        this.taskDispatcher = new TaskDispatcher(platformScheduler, taskRunner);
        this.metadataFactory = Objects.requireNonNull(metadataFactory, "metadataFactory");
    }

    SchedulerTask async(String name, Runnable runnable) {
        var metadata = metadataFactory.create(name, ExecutionTarget.async());
        return platformScheduler.runAsync(metadata, taskRunner.wrap(metadata, runnable));
    }

    SchedulerTask asyncLater(String name, Runnable runnable, Duration delay) {
        var metadata = metadataFactory.create(name, ExecutionTarget.async());
        return platformScheduler.runAsyncLater(metadata, taskRunner.wrap(metadata, runnable), delay);
    }

    SchedulerTask asyncTimer(String name, Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadataFactory.create(name, ExecutionTarget.async());
        return platformScheduler.runAsyncTimer(metadata, taskRunner.wrap(metadata, runnable), initialDelay, period);
    }

    SchedulerTask global(String name, Runnable runnable) {
        var metadata = metadataFactory.create(name, ExecutionTarget.global());
        return platformScheduler.runGlobal(metadata, taskRunner.wrap(metadata, runnable));
    }

    SchedulerTask globalLater(String name, Runnable runnable, Duration delay) {
        var metadata = metadataFactory.create(name, ExecutionTarget.global());
        return platformScheduler.runGlobalLater(metadata, taskRunner.wrap(metadata, runnable), delay);
    }

    SchedulerTask globalTimer(String name, Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadataFactory.create(name, ExecutionTarget.global());
        return platformScheduler.runGlobalTimer(metadata, taskRunner.wrap(metadata, runnable), initialDelay, period);
    }

    SchedulerTask region(String name, Location location, Runnable runnable) {
        var metadata = metadataFactory.create(name, ExecutionTarget.region(location));
        return platformScheduler.runRegion(metadata, location, taskRunner.wrap(metadata, runnable));
    }

    SchedulerTask region(String name, UUID worldId, int chunkX, int chunkZ, Runnable runnable) {
        var target = ExecutionTarget.region(worldId, chunkX, chunkZ);
        var metadata = metadataFactory.create(name, target);
        return platformScheduler.runRegion(metadata, worldId, chunkX, chunkZ, taskRunner.wrap(metadata, runnable));
    }

    SchedulerTask regionLater(String name, Location location, Runnable runnable, Duration delay) {
        var metadata = metadataFactory.create(name, ExecutionTarget.region(location));
        return platformScheduler.runRegionLater(metadata, location, taskRunner.wrap(metadata, runnable), delay);
    }

    SchedulerTask regionTimer(
            String name, Location location, Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadataFactory.create(name, ExecutionTarget.region(location));
        return platformScheduler.runRegionTimer(
                metadata, location, taskRunner.wrap(metadata, runnable), initialDelay, period);
    }

    SchedulerTask entity(String name, Entity entity, Runnable runnable) {
        var metadata = metadataFactory.create(name, ExecutionTarget.entity(entity));
        return platformScheduler.runEntity(
                metadata, entity, taskRunner.wrap(metadata, runnable), () -> taskErrorReporter.handleRetired(metadata));
    }

    SchedulerTask entity(String name, UUID entityId, Runnable runnable) {
        var target = ExecutionTarget.entity(entityId);
        var metadata = metadataFactory.create(name, target);
        return platformScheduler.runEntity(
                metadata,
                entityId,
                taskRunner.wrap(metadata, runnable),
                () -> taskErrorReporter.handleRetired(metadata));
    }

    SchedulerTask entityLater(String name, Entity entity, Runnable runnable, Duration delay) {
        var metadata = metadataFactory.create(name, ExecutionTarget.entity(entity));
        return platformScheduler.runEntityLater(
                metadata,
                entity,
                taskRunner.wrap(metadata, runnable),
                () -> taskErrorReporter.handleRetired(metadata),
                delay);
    }

    SchedulerTask entityTimer(String name, Entity entity, Runnable runnable, Duration initialDelay, Duration period) {
        var metadata = metadataFactory.create(name, ExecutionTarget.entity(entity));
        return platformScheduler.runEntityTimer(
                metadata,
                entity,
                taskRunner.wrap(metadata, runnable),
                () -> taskErrorReporter.handleRetired(metadata),
                initialDelay,
                period);
    }

    <T> CompletableFuture<T> supply(ExecutionTarget target, String name, Supplier<T> supplier) {
        var future = new CompletableFuture<T>();
        var metadata = metadataFactory.create(name, target);
        Runnable runnable = () -> taskRunner.complete(metadata, supplier, future);
        taskDispatcher.dispatchPrepared(target, metadata, runnable, future);
        return future;
    }

    Executor asyncExecutor() {
        return asyncExecutor;
    }

    Executor globalExecutor() {
        return globalExecutor;
    }

    Executor regionExecutor(Location location) {
        return command -> region("executor-region", location, command);
    }

    Executor regionExecutor(UUID worldId, int chunkX, int chunkZ) {
        return command -> region("executor-region", worldId, chunkX, chunkZ, command);
    }

    Executor entityExecutor(Entity entity) {
        return command -> entity("executor-entity", entity, command);
    }

    Executor entityExecutor(UUID entityId) {
        return command -> entity("executor-entity", entityId, command);
    }

    @Override
    public SchedulerTask execute(String name, Runnable runnable) {
        return async(name, runnable);
    }

    @Override
    public SchedulerTask schedule(String name, Runnable runnable, Duration delay) {
        return asyncLater(name, runnable, delay);
    }
}
