package com.cotani.task.internal.task;

import com.cotani.api.InternalApi;
import com.cotani.task.api.SchedulerTask;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@InternalApi
public final class FutureSchedulerTask implements SchedulerTask {
    private final Future<Void> future;
    private final Runnable onTerminal;
    private final AtomicBoolean cancelled;
    private final AtomicBoolean terminalNotified;

    public FutureSchedulerTask(Future<Void> future) {
        this(future, () -> {});
    }

    public FutureSchedulerTask(Future<Void> future, Runnable onTerminal) {
        this.future = Objects.requireNonNull(future, "future");
        this.onTerminal = Objects.requireNonNull(onTerminal, "onTerminal");
        this.cancelled = new AtomicBoolean(false);
        this.terminalNotified = new AtomicBoolean(false);
    }

    @Override
    public boolean cancel() {
        if (cancelled.compareAndSet(false, true)) {
            future.cancel(true);
            notifyTerminal();
            return true;
        }

        return false;
    }

    @Override
    public boolean cancelled() {
        return cancelled.get() || future.isCancelled();
    }

    private void notifyTerminal() {
        if (terminalNotified.compareAndSet(false, true)) {
            onTerminal.run();
        }
    }
}
