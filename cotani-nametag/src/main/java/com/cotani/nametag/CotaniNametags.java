package com.cotani.nametag;

import com.cotani.nametag.api.NametagModule;
import com.cotani.nametag.internal.DefaultNametagModule;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Main factory for bootstrapping and configuring the Cotani Nametag module.
 */
public final class CotaniNametags {

    private CotaniNametags() {}

    /**
     * Creates and registers a new {@link NametagModule} instance.
     *
     * @param plugin the owning Paper/Folia plugin
     * @param scheduler the Cotani Paper task scheduler
     * @return the created NametagModule
     */
    public static NametagModule create(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");

        return new DefaultNametagModule(plugin, scheduler);
    }
}
