package com.cotani.task.api;

import java.util.Optional;

public final class TaskContextHolder {

    public static final ScopedValue<TaskContext> CURRENT = ScopedValue.newInstance();

    private TaskContextHolder() {}

    public static Optional<TaskContext> find() {
        if (CURRENT.isBound()) {
            return Optional.of(CURRENT.get());
        }

        return Optional.empty();
    }
}
