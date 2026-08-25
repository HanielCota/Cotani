package com.cotani.display;

import com.cotani.display.api.DisplayModule;
import com.cotani.display.internal.DefaultDisplayModule;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Main factory for bootstrapping and configuring the Cotani Display module.
 */
public final class CotaniDisplays {

    private CotaniDisplays() {}

    /**
     * Creates and registers a new {@link DisplayModule} instance.
     *
     * @param plugin the owning Paper/Folia plugin
     * @param scheduler the Cotani Paper task scheduler
     * @return the created DisplayModule
     */
    public static DisplayModule create(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(scheduler, "scheduler cannot be null");
        return new DefaultDisplayModule(plugin, scheduler);
    }
}
