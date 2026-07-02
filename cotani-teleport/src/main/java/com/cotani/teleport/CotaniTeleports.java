package com.cotani.teleport;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.TeleportMessages;
import com.cotani.teleport.api.TeleportModule;
import com.cotani.teleport.impl.DefaultTeleportModule;
import org.bukkit.plugin.Plugin;

public final class CotaniTeleports {

    private CotaniTeleports() {}

    public static TeleportModule create(Plugin plugin, PaperTaskScheduler scheduler) {
        return DefaultTeleportModule.create(plugin, scheduler);
    }

    public static TeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler) {
        return DefaultTeleportModule.create(plugin, combatAdapter, regionAdapter, scheduler);
    }

    public static TeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler,
            TeleportMessages messages) {
        return DefaultTeleportModule.create(plugin, combatAdapter, regionAdapter, scheduler, messages);
    }
}
