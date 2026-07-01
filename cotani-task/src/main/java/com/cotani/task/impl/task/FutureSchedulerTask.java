package com.cotani.task.impl.task;

import com.cotani.task.api.SchedulerTask;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FutureSchedulerTask implements SchedulerTask {

    private final Future<Void> future;
    private final AtomicBoolean cancelled;

    public FutureSchedulerTask(Future<Void> future) {
        this.future = Objects.requireNonNull(future, "future");
        this.cancelled = new AtomicBoolean(false);
    }

    @Override
    public boolean cancel() {
        if (cancelled.compareAndSet(false, true)) {
            future.cancel(true);
            return true;
        }

        return false;
    }

    @Override
    public boolean cancelled() {
        return cancelled.get() || future.isCancelled();
    }
}
