package com.cotani.task.exception;

import java.io.Serial;

/** Signals that a task failed with a checked exception that cannot be propagated directly. */
public final class TaskExecutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public TaskExecutionException(Throwable cause) {
        super("Task execution failed.", cause);
    }
}
