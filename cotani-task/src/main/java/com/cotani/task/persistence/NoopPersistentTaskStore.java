package com.cotani.task.persistence;

import java.util.List;

public final class NoopPersistentTaskStore implements PersistentTaskStore {

    @Override
    public void save(PersistentTask task) {
        // Noop store does not persist tasks.
    }

    @Override
    public List<PersistentTask> loadPending() {
        return List.of();
    }

    @Override
    public void markCompleted(PersistentTask task) {
        // Noop store does not track completion.
    }
}
