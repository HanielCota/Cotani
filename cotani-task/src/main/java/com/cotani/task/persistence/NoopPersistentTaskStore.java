package com.cotani.task.persistence;

import java.util.List;
import java.util.Objects;

public final class NoopPersistentTaskStore implements PersistentTaskStore {

    @Override
    public void save(PersistentTask task) {
        Objects.requireNonNull(task, "task");
    }

    @Override
    public List<PersistentTask> loadPending() {
        return List.of();
    }

    @Override
    public void markCompleted(PersistentTask task) {
        Objects.requireNonNull(task, "task");
    }
}
