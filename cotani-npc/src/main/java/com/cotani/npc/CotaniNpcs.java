package com.cotani.npc;

import com.cotani.npc.api.NpcModule;
import com.cotani.npc.impl.DefaultNpcModule;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

/**
 * Main factory for creating and configuring the Cotani NPC module.
 */
public final class CotaniNpcs {

    private CotaniNpcs() {}

    /**
     * Creates and registers a new {@link NpcModule} instance.
     *
     * @param plugin the owning Paper/Folia plugin
     * @param scheduler the Cotani Paper task scheduler
     * @return the created NpcModule
     */
    public static NpcModule create(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");

        return new DefaultNpcModule(plugin, scheduler);
    }
}
