package com.cotani.teleport.impl;

import com.cotani.Cotani;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.impl.scheduler.SchedulerFactory;
import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.CotaniTeleport;
import com.cotani.teleport.api.PendingTeleportService;
import com.cotani.teleport.api.TeleportMessages;
import com.cotani.teleport.api.TeleportService;
import com.cotani.teleport.event.TeleportEventBus;
import com.cotani.teleport.pending.DefaultPendingTeleportService;
import com.cotani.teleport.pending.PendingTeleportListener;
import com.cotani.teleport.policy.*;
import com.cotani.teleport.safety.DefaultSafeLocationResolver;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class TeleportModule implements AutoCloseable {
    private final Cotani cotani;
    private final PaperTaskScheduler scheduler;
    private final TeleportService teleportService;
    private final PendingTeleportService pendingTeleportService;
    private final TeleportCooldownService cooldownService;

    private TeleportModule(
            Cotani cotani,
            PaperTaskScheduler scheduler,
            TeleportService teleportService,
            PendingTeleportService pendingTeleportService,
            TeleportCooldownService cooldownService) {
        this.cotani = cotani;
        this.scheduler = scheduler;
        this.teleportService = teleportService;
        this.pendingTeleportService = pendingTeleportService;
        this.cooldownService = cooldownService;
    }

    public static TeleportModule create(Plugin plugin) {
        return create(plugin, CombatAdapter.noop(), RegionProtectionAdapter.noop(), TeleportMessages.defaults());
    }

    public static TeleportModule create(
            Plugin plugin, CombatAdapter combatAdapter, RegionProtectionAdapter regionAdapter) {
        return create(plugin, combatAdapter, regionAdapter, TeleportMessages.defaults());
    }

    public static TeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            TeleportMessages messages) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(combatAdapter, "combatAdapter");
        Objects.requireNonNull(regionAdapter, "regionAdapter");
        Objects.requireNonNull(messages, "messages");

        PaperTaskScheduler scheduler = SchedulerFactory.create(plugin);
        Clock clock = Clock.systemUTC();

        TeleportCooldownService cooldownService = new TeleportCooldownService(clock, scheduler);

        TeleportPolicyChain policyChain = new TeleportPolicyChain(List.of(
                new PermissionTeleportPolicy("cotani.teleport.use", messages),
                new CombatTeleportPolicy(combatAdapter, messages),
                new CooldownTeleportPolicy(cooldownService, messages),
                new RegionTeleportPolicy(regionAdapter, messages)));

        TeleportEventBus eventBus = new TeleportEventBus(scheduler);
        TeleportEventNotifier eventNotifier = new TeleportEventNotifier(eventBus, clock);
        TeleportResultMapper resultMapper = new TeleportResultMapper(eventNotifier);

        TeleportService teleportService = new PaperTeleportService(
                policyChain,
                new DefaultSafeLocationResolver(scheduler),
                eventNotifier,
                resultMapper,
                cooldownService,
                scheduler,
                clock);

        DefaultPendingTeleportService pendingTeleportService =
                new DefaultPendingTeleportService(teleportService, scheduler);

        Cotani cotani = Cotani.forPlugin(plugin)
                .with(scheduler)
                .with(cooldownService)
                .with(pendingTeleportService)
                .build();

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new PendingTeleportListener(pendingTeleportService), plugin);

        CotaniTeleport.init(teleportService, pendingTeleportService);

        return new TeleportModule(cotani, scheduler, teleportService, pendingTeleportService, cooldownService);
    }

    public Cotani cotani() {
        return cotani;
    }

    public TeleportService teleportService() {
        return teleportService;
    }

    public PendingTeleportService pendingTeleportService() {
        return pendingTeleportService;
    }

    public TeleportCooldownService cooldownService() {
        return cooldownService;
    }

    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void close() {
        CotaniTeleport.shutdown();
        cotani.close();
    }
}
