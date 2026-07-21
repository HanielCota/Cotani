package com.cotani.teleport.impl;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.scheduler.SchedulerFactory;
import com.cotani.teleport.CotaniTeleports;
import com.cotani.teleport.api.TeleportModule;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class CotaniTeleportPlugin extends JavaPlugin {
    private @Nullable PaperTaskScheduler scheduler;
    private @Nullable TeleportModule module;

    @Override
    @SuppressWarnings("deprecation")
    public void onEnable() {
        PaperTaskScheduler createdScheduler = SchedulerFactory.create(this);
        try {
            // NOTE: real CombatAdapter/RegionProtectionAdapter must be wired here for production use.
            // The 2-arg create(...) uses noop adapters and is deprecated/test-only.
            module = CotaniTeleports.create(this, createdScheduler);
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
