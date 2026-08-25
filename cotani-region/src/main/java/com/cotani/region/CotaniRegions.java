package com.cotani.region;

import com.cotani.region.api.RegionModule;
import com.cotani.region.internal.DefaultRegionModule;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Factory for creating and configuring the Cotani 3D Region and protection module.
 */
public final class CotaniRegions {

    private CotaniRegions() {}

    /**
     * Creates a new {@link RegionModule} instance.
     *
     * @param plugin the owning Paper/Folia plugin
     * @param scheduler the Cotani task scheduler
     * @return the created RegionModule
     */
    public static RegionModule create(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");

        return new DefaultRegionModule(plugin, scheduler);
    }
}
