package com.cotani.placeholder;

import com.cotani.placeholder.api.PlaceholderService;
import com.cotani.placeholder.impl.DefaultPlaceholderService;
import com.cotani.task.CotaniTasks;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

/**
 * Entrypoint factory for the {@code cotani-placeholder} module.
 */
@NullMarked
public final class CotaniPlaceholders {

    private CotaniPlaceholders() {}

    /**
     * Creates and registers a new {@link PlaceholderService} for the given plugin.
     *
     * @param plugin owning plugin
     * @return new placeholder service instance
     */
    public static PlaceholderService create(Plugin plugin) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        PaperTaskScheduler scheduler = CotaniTasks.create(plugin);
        return create(plugin, scheduler);
    }

    /**
     * Creates and registers a new {@link PlaceholderService} using the given scheduler.
     *
     * @param plugin owning plugin
     * @param scheduler task scheduler
     * @return new placeholder service instance
     */
    public static PlaceholderService create(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
        return new DefaultPlaceholderService(plugin, scheduler);
    }
}
