package com.cotani.task.impl.dispatch;

import com.cotani.api.InternalApi;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.TaskMetadata;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@InternalApi
public final class TaskDispatcher {

    private final PlatformScheduler platformScheduler;
    private final TaskRunner taskRunner;

    private TaskDispatcher(PlatformScheduler platformScheduler, TaskRunner taskRunner) {
        this.platformScheduler = Objects.requireNonNull(platformScheduler, "platformScheduler");
        this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
    }

    public static TaskDispatcher create(PlatformScheduler platformScheduler, TaskRunner taskRunner) {
        return new TaskDispatcher(platformScheduler, taskRunner);
    }

    public <T> void dispatch(
            ExecutionTarget target, TaskMetadata metadata, Runnable runnable, CompletableFuture<T> future) {
        dispatchPrepared(target, metadata, taskRunner.wrap(metadata, runnable), future);
    }

    /** Dispatches work that already owns its metrics and exception-reporting lifecycle. */
    public <T> void dispatchPrepared(
            ExecutionTarget target, TaskMetadata metadata, Runnable runnable, CompletableFuture<T> future) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(future, "future");

        switch (target) {
            case ExecutionTarget.Async() -> platformScheduler.runAsync(metadata, runnable);
            case ExecutionTarget.Global() -> platformScheduler.runGlobal(metadata, runnable);
            case ExecutionTarget.Region region ->
                platformScheduler.runRegion(metadata, region.worldId(), region.chunkX(), region.chunkZ(), runnable);
            case ExecutionTarget.EntityTarget entityTarget ->
                platformScheduler.runEntity(metadata, entityTarget.entityId(), runnable, () -> retire(future));
        }
    }

    private <T> void retire(CompletableFuture<T> future) {
        future.completeExceptionally(new IllegalStateException("Entity scheduler retired before task execution."));
    }
}
