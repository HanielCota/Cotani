package com.cotani.teleport.impl;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.scheduler.SchedulerFactory;
import com.cotani.teleport.CotaniTeleports;
import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.TeleportModule;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

@com.cotani.api.InternalApi
public final class CotaniTeleportPlugin extends JavaPlugin {
    private @Nullable PaperTaskScheduler scheduler;
    private @Nullable TeleportModule module;

    @Override
    public void onEnable() {
        PaperTaskScheduler createdScheduler = SchedulerFactory.create(this);
        try {
            // NOTE: wire real CombatAdapter/RegionProtectionAdapter integrations for production use.
            module = CotaniTeleports.create(
                    this, CombatAdapter.noop(), RegionProtectionAdapter.noop(), createdScheduler);
            scheduler = createdScheduler;
        } catch (RuntimeException failure) {
            createdScheduler.close();
            throw failure;
        }
    }

    @Override
    public void onDisable() {
        TeleportModule m = module;
        PaperTaskScheduler s = scheduler;
        module = null;
        scheduler = null;
        if (m != null) {
            m.close();
        }
        if (s != null) {
            s.close();
        }
    }
}
