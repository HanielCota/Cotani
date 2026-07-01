package com.cotani.task.impl.dispatch;

import com.cotani.task.api.TaskContext;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.api.TaskMetadata;
import java.util.Objects;

public final class TaskErrorReporter {

    private final TaskExceptionHandler exceptionHandler;

    public TaskErrorReporter(TaskExceptionHandler exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    public void handleRetired(TaskMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");

        var context = TaskContext.start(metadata);

        exceptionHandler.handle(context, new IllegalStateException("Entity scheduler retired before task execution."));
    }

    public void closePlatform(TaskMetadata metadata, AutoCloseable closeable) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(closeable, "closeable");

        try {
            closeable.close();
        } catch (Exception exception) {
            var context = TaskContext.start(metadata);

            exceptionHandler.handle(context, exception);
        }
    }
}
