package com.cotani.task.impl.task;

import com.cotani.task.api.SchedulerTask;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Objects;

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
