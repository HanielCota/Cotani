package com.cotani.task.impl.task;

import com.cotani.task.api.SchedulerTask;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class LazySchedulerTask implements SchedulerTask {

    private final AtomicReference<@Nullable SchedulerTask> setupTask;
    private final AtomicReference<@Nullable SchedulerTask> delegate;
    private final AtomicBoolean cancelled;

    public LazySchedulerTask() {
        this.setupTask = new AtomicReference<>();
        this.delegate = new AtomicReference<>();
        this.cancelled = new AtomicBoolean(false);
    }

    public void setSetupTask(SchedulerTask setupTask) {
        Objects.requireNonNull(setupTask, "setupTask");

        this.setupTask.set(setupTask);

        if (cancelled.get()) {
            setupTask.cancel();
        }
    }

    public void setDelegate(SchedulerTask delegate) {
        Objects.requireNonNull(delegate, "delegate");

        this.delegate.set(delegate);

        if (cancelled.get()) {
            delegate.cancel();
        }
    }

    @Override
    public boolean cancel() {
        cancelled.set(true);

        SchedulerTask setup = setupTask.get();

        if (setup != null) {
            setup.cancel();
        }

        SchedulerTask task = delegate.get();

        if (task != null) {
            task.cancel();
        }

        return true;
    }

    @Override
    public boolean cancelled() {
        SchedulerTask setup = setupTask.get();
        SchedulerTask task = delegate.get();

        return cancelled.get() || (setup != null && setup.cancelled()) || (task != null && task.cancelled());
    }
}
