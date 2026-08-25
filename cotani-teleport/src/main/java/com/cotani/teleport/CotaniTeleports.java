package com.cotani.teleport;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.TeleportMessages;
import com.cotani.teleport.api.TeleportModule;
import com.cotani.teleport.internal.DefaultTeleportModule;
import org.bukkit.plugin.Plugin;

public final class CotaniTeleports {
    private CotaniTeleports() {}

    public static TeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        java.util.Objects.requireNonNull(combatAdapter, "combatAdapter");
        java.util.Objects.requireNonNull(regionAdapter, "regionAdapter");
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        return DefaultTeleportModule.create(plugin, combatAdapter, regionAdapter, scheduler);
    }

    public static TeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler,
            TeleportMessages messages) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        java.util.Objects.requireNonNull(combatAdapter, "combatAdapter");
        java.util.Objects.requireNonNull(regionAdapter, "regionAdapter");
        java.util.Objects.requireNonNull(scheduler, "scheduler");
        java.util.Objects.requireNonNull(messages, "messages");
        return DefaultTeleportModule.create(plugin, combatAdapter, regionAdapter, scheduler, messages);
    }
}
