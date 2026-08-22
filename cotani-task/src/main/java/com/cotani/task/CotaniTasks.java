package com.cotani.task;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.scheduler.SchedulerFactory;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Entrypoint factory for the {@code cotani-task} module.
 */
public final class CotaniTasks {

    private CotaniTasks() {}

    /**
     * Creates and initializes a {@link PaperTaskScheduler} instance for the given plugin.
     *
     * @param plugin owning plugin
     * @return scheduler instance
     */
    public static PaperTaskScheduler create(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return SchedulerFactory.create(plugin);
    }
}
