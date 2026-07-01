package com.cotani.task.persistence;

import java.util.List;

public interface PersistentTaskStore {

    void save(PersistentTask task);

    List<PersistentTask> loadPending();

    void markCompleted(PersistentTask task);
}
