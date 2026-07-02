package com.cotani.task;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerOptions;
import com.cotani.task.api.TaskChain;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.impl.scheduler.SchedulerFactory;
import com.cotani.task.metrics.TaskMetrics;
import com.cotani.task.persistence.PersistentTaskStore;
import java.util.concurrent.CompletionStage;
import org.bukkit.plugin.Plugin;

public final class CotaniTasks {

    private CotaniTasks() {}

    public static PaperTaskScheduler create(Plugin plugin) {
        return SchedulerFactory.create(plugin);
    }

    public static PaperTaskScheduler create(Plugin plugin, SchedulerOptions options) {
        return SchedulerFactory.create(plugin, options);
    }

    public static PaperTaskScheduler create(
            Plugin plugin, SchedulerOptions options, TaskExceptionHandler exceptionHandler) {
        return SchedulerFactory.create(plugin, options, exceptionHandler);
    }

    public static PaperTaskScheduler create(
            Plugin plugin, SchedulerOptions options, TaskExceptionHandler exceptionHandler, TaskMetrics metrics) {
        return SchedulerFactory.create(plugin, options, exceptionHandler, metrics);
    }

    public static PaperTaskScheduler create(
            Plugin plugin,
            SchedulerOptions options,
            TaskExceptionHandler exceptionHandler,
            TaskMetrics metrics,
            PersistentTaskStore persistentTaskStore) {
        return SchedulerFactory.create(plugin, options, exceptionHandler, metrics, persistentTaskStore);
    }

    public static <T> TaskChain<T> chain(PaperTaskScheduler scheduler, CompletionStage<T> stage) {
        return scheduler.chain(stage);
    }
}