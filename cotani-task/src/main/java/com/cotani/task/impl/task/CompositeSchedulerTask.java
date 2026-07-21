package com.cotani.task.impl.task;

import com.cotani.task.api.SchedulerTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A scheduler task that cancels both a setup task and the task it eventually delegates to.
 *
 * <p>Used when a task must first run on the global region thread to resolve a Bukkit object
 * (World/Entity by UUID) before scheduling the real work on a region/entity thread.
 */
public final class CompositeSchedulerTask implements SchedulerTask {

    private final SchedulerTask setupTask;
    private final AtomicReference<SchedulerTask> delegate;

    public CompositeSchedulerTask(SchedulerTask setupTask, AtomicReference<SchedulerTask> delegate) {
        this.setupTask = setupTask;
        this.delegate = delegate;
    }

    @Override
    public boolean cancel() {
        boolean cancelled = setupTask.cancel();
        SchedulerTask scheduled = delegate.get();
        if (scheduled != null) {
            cancelled |= scheduled.cancel();
        }
        return cancelled;
    }

    @Override
    public boolean cancelled() {
        SchedulerTask scheduled = delegate.get();
        return setupTask.cancelled() || (scheduled != null && scheduled.cancelled());
    }
}
