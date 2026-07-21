package com.cotani.task.impl.dispatch;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PlatformScheduler;
import com.cotani.task.api.TaskMetadata;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class TaskDispatcher {

    private final PlatformScheduler platformScheduler;
    private final TaskRunner taskRunner;

    public TaskDispatcher(PlatformScheduler platformScheduler, TaskRunner taskRunner) {
        this.platformScheduler = Objects.requireNonNull(platformScheduler, "platformScheduler");
        this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
    }

    public <T> void dispatch(
            ExecutionTarget target, TaskMetadata metadata, Runnable runnable, CompletableFuture<T> future) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(future, "future");

        Runnable wrapped = taskRunner.wrap(metadata, runnable);

        switch (target) {
            case ExecutionTarget.Async() -> platformScheduler.runAsync(metadata, wrapped);
            case ExecutionTarget.Global() -> platformScheduler.runGlobal(metadata, wrapped);
            case ExecutionTarget.Region region ->
                platformScheduler.runRegion(metadata, region.worldId(), region.chunkX(), region.chunkZ(), wrapped);
            case ExecutionTarget.EntityTarget entityTarget ->
                platformScheduler.runEntity(metadata, entityTarget.entityId(), wrapped, () -> retire(future));
        }
    }

    private <T> void retire(CompletableFuture<T> future) {
        future.completeExceptionally(new IllegalStateException("Entity scheduler retired before task execution."));
    }
}
