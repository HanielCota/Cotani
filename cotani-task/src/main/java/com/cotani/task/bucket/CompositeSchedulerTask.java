package com.cotani.task.bucket;

import com.cotani.task.api.SchedulerTask;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

final class CompositeSchedulerTask implements SchedulerTask {

    private final SchedulerTask immediate;
    private final AtomicReference<@Nullable SchedulerTask> rescheduled;

    CompositeSchedulerTask(SchedulerTask immediate, AtomicReference<@Nullable SchedulerTask> rescheduled) {
        this.immediate = immediate;
        this.rescheduled = rescheduled;
    }

    @Override
    public boolean cancel() {
        boolean cancelled = immediate.cancel();

        SchedulerTask later = rescheduled.get();

        if (later != null) {
            cancelled |= later.cancel();
        }

        return cancelled;
    }

    @Override
    public boolean cancelled() {
        SchedulerTask later = rescheduled.get();

        return immediate.cancelled() || (later != null && later.cancelled());
    }
}
