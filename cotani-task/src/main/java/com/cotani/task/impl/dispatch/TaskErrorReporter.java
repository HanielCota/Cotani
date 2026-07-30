package com.cotani.task.impl.dispatch;

import com.cotani.api.InternalApi;
import com.cotani.task.api.TaskContext;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.api.TaskMetadata;
import java.util.Objects;

@InternalApi
public final class TaskErrorReporter {

    private final TaskExceptionHandler exceptionHandler;

    private TaskErrorReporter(TaskExceptionHandler exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    public static TaskErrorReporter create(TaskExceptionHandler exceptionHandler) {
        return new TaskErrorReporter(exceptionHandler);
    }

    public void handleRetired(TaskMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        var context = TaskContext.start(metadata);

        exceptionHandler.handle(context, new IllegalStateException("Entity scheduler retired before task execution."));
    }
}
