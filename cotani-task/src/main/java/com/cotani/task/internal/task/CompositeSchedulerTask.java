package com.cotani.task.internal.task;

import com.cotani.api.InternalApi;
import com.cotani.task.api.SchedulerTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A scheduler task that cancels both a setup task and the task it eventually delegates to.
 *
 * <p>Used when a task must first run on the global region thread to resolve a Bukkit object
 * (World/Entity by UUID) before scheduling the real work on a region/entity thread.
 */
@InternalApi
public final class CompositeSchedulerTask implements SchedulerTask {
    private final SchedulerTask setupTask;
    private final AtomicReference<SchedulerTask> delegate;
    private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();

    public CompositeSchedulerTask(SchedulerTask setupTask, AtomicReference<SchedulerTask> delegate) {
        this.setupTask = setupTask;
        this.delegate = delegate;
    }

    public void assignDelegate(SchedulerTask task) {
        delegate.set(task);
        if (cancelled.get() || setupTask.cancelled()) {
            task.cancel();
        }
    }

    @Override
    public boolean cancel() {
        cancelled.set(true);
        boolean cancelledSetup = setupTask.cancel();
        SchedulerTask scheduled = delegate.get();

        if (scheduled != null) {
            cancelledSetup |= scheduled.cancel();
        }
        return cancelledSetup;
    }

    @Override
    public boolean cancelled() {
        SchedulerTask scheduled = delegate.get();
        return cancelled.get() || setupTask.cancelled() || (scheduled != null && scheduled.cancelled());
    }
}
