package com.cotani.task.exception;

import java.time.Duration;
import java.util.Objects;

public class TaskTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Duration timeout;

    public TaskTimeoutException(Duration timeout) {
        super("Task did not complete within " + timeout);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public Duration timeout() {
        return timeout;
    }
}
