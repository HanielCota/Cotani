package com.cotani.hud;

import com.cotani.hud.api.HudModule;
import com.cotani.hud.impl.DefaultHudModule;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Main factory for bootstrapping and configuring the Cotani HUD module.
 */
public final class CotaniHuds {

    private CotaniHuds() {}

    /**
     * Creates and registers a new {@link HudModule} instance.
     *
     * @param plugin the owning Paper/Folia plugin
     * @param scheduler the Cotani Paper task scheduler
     * @return the created HudModule
     */
    public static HudModule create(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");

        return new DefaultHudModule(plugin, scheduler);
    }
}
