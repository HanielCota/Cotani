package com.cotani.task.internal.task;

import com.cotani.api.InternalApi;
import com.cotani.task.api.SchedulerTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;

@InternalApi
public record PaperSchedulerTask(ScheduledTask task) implements SchedulerTask {
    public PaperSchedulerTask {
        Objects.requireNonNull(task, "task");
    }

    @Override
    public boolean cancel() {
        task.cancel();

        return true;
    }

    @Override
    public boolean cancelled() {
        return task.isCancelled();
    }
}
