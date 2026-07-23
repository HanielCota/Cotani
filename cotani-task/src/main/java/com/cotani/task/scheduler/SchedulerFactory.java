package com.cotani.task.scheduler;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerOptions;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.impl.exception.LoggerTaskExceptionHandler;
import com.cotani.task.impl.scheduler.ModernPaperTaskScheduler;
import com.cotani.task.impl.scheduler.PlatformSchedulerFactory;
import com.cotani.task.metrics.DefaultTaskMetrics;
import com.cotani.task.metrics.TaskMetrics;
import com.cotani.task.persistence.NoopPersistentTaskStore;
import com.cotani.task.persistence.PersistentTaskStore;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class SchedulerFactory {

    private static final String PLUGIN_PARAM = "plugin";
    private static final String OPTIONS_PARAM = "options";
    private static final String EXCEPTION_HANDLER_PARAM = "exceptionHandler";

    private SchedulerFactory() {}

    public static PaperTaskScheduler create(Plugin plugin) {
        Objects.requireNonNull(plugin, PLUGIN_PARAM);

        return create(plugin, SchedulerOptions.defaults());
    }

    public static PaperTaskScheduler create(Plugin plugin, SchedulerOptions options) {
        Objects.requireNonNull(plugin, PLUGIN_PARAM);
        Objects.requireNonNull(options, OPTIONS_PARAM);

        return create(plugin, options, new LoggerTaskExceptionHandler(plugin.getLogger()));
    }

    public static PaperTaskScheduler create(
            Plugin plugin, SchedulerOptions options, TaskExceptionHandler exceptionHandler) {
        Objects.requireNonNull(plugin, PLUGIN_PARAM);
        Objects.requireNonNull(options, OPTIONS_PARAM);
        Objects.requireNonNull(exceptionHandler, EXCEPTION_HANDLER_PARAM);

        return create(plugin, options, exceptionHandler, new DefaultTaskMetrics());
    }

    public static PaperTaskScheduler create(
            Plugin plugin, SchedulerOptions options, TaskExceptionHandler exceptionHandler, TaskMetrics metrics) {
        Objects.requireNonNull(plugin, PLUGIN_PARAM);
        Objects.requireNonNull(options, OPTIONS_PARAM);
        Objects.requireNonNull(exceptionHandler, EXCEPTION_HANDLER_PARAM);
        Objects.requireNonNull(metrics, "metrics");

        return create(plugin, options, exceptionHandler, metrics, new NoopPersistentTaskStore());
    }

    public static PaperTaskScheduler create(
            Plugin plugin,
            SchedulerOptions options,
            TaskExceptionHandler exceptionHandler,
            TaskMetrics metrics,
            PersistentTaskStore persistentTaskStore) {
        Objects.requireNonNull(plugin, PLUGIN_PARAM);
        Objects.requireNonNull(options, OPTIONS_PARAM);
        Objects.requireNonNull(exceptionHandler, EXCEPTION_HANDLER_PARAM);
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(persistentTaskStore, "persistentTaskStore");

        var platformScheduler = PlatformSchedulerFactory.create(plugin, options);

        return new ModernPaperTaskScheduler(platformScheduler, exceptionHandler, options, metrics, persistentTaskStore);
    }
}
