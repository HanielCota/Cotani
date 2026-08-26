package com.cotani.task.internal.task;

import com.cotani.api.InternalApi;
import com.cotani.task.api.SchedulerTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@InternalApi
public final class PaperSchedulerTask implements SchedulerTask {
    private final ScheduledTask task;
    private final Runnable onTerminal;
    private final AtomicBoolean terminalNotified = new AtomicBoolean();

    public PaperSchedulerTask(ScheduledTask task) {
        this(task, () -> {});
    }

    public PaperSchedulerTask(ScheduledTask task, Runnable onTerminal) {
        this.task = Objects.requireNonNull(task, "task");
        this.onTerminal = Objects.requireNonNull(onTerminal, "onTerminal");
    }

    @Override
    public boolean cancel() {
        task.cancel();
        notifyTerminal();

        return true;
    }

    @Override
    public boolean cancelled() {
        return task.isCancelled();
    }

    private void notifyTerminal() {
        if (terminalNotified.compareAndSet(false, true)) {
            onTerminal.run();
        }
    }
}
