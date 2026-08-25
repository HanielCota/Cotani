package com.cotani.cleanup.paper;

import com.cotani.task.api.SchedulerTask;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellable recurring cleanup trigger owned by the caller's scheduler lifecycle. */
public final class CleanupSchedule implements AutoCloseable {
    private final SchedulerTask task;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    CleanupSchedule(SchedulerTask task) {
        this.task = Objects.requireNonNull(task, "task");
    }

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        return task.cancel();
    }

    public boolean cancelled() {
        return cancelled.get() || task.cancelled();
    }

    @Override
    public void close() {
        cancel();
    }
}
