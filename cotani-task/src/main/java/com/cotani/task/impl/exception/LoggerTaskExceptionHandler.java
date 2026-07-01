package com.cotani.task.impl.exception;

import com.cotani.task.api.TaskContext;
import com.cotani.task.api.TaskExceptionHandler;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public record LoggerTaskExceptionHandler(Logger logger) implements TaskExceptionHandler {

    public LoggerTaskExceptionHandler {
        Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void handle(TaskContext context, Throwable throwable) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(throwable, "throwable");

        var taskName = context.metadata().name();
        var message = "Task failed: " + taskName + " after " + context.elapsedMillis() + "ms";

        logger.log(Level.SEVERE, message, throwable);
    }
}
