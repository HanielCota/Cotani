package com.cotani.teleport.impl;

import com.cotani.Cotani;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.PendingTeleportService;
import com.cotani.teleport.api.TeleportMessages;
import com.cotani.teleport.api.TeleportModule;
import com.cotani.teleport.api.TeleportService;
import com.cotani.teleport.config.TeleportConfiguration;
import com.cotani.teleport.config.TeleportOptionsFactory;
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
import org.jspecify.annotations.Nullable;

public final class DefaultTeleportModule implements TeleportModule {
    private final Cotani cotani;
    private final PaperTaskScheduler scheduler;
    private final TeleportService teleportService;
    private final PendingTeleportService pendingTeleportService;
    private final TeleportCooldownService cooldownService;
    private final TeleportOptionsFactory options;

    private DefaultTeleportModule(
            Cotani cotani,
            PaperTaskScheduler scheduler,
            TeleportService teleportService,
            PendingTeleportService pendingTeleportService,
            TeleportCooldownService cooldownService,
            TeleportOptionsFactory options) {
        this.cotani = cotani;
        this.scheduler = scheduler;
        this.teleportService = teleportService;
        this.pendingTeleportService = pendingTeleportService;
        this.cooldownService = cooldownService;
        this.options = options;
    }

    public static DefaultTeleportModule create(Plugin plugin, PaperTaskScheduler scheduler) {
        return create(plugin, CombatAdapter.noop(), RegionProtectionAdapter.noop(), scheduler);
    }

    public static DefaultTeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        TeleportConfiguration configuration;
        try {
            configuration = TeleportConfiguration.load(plugin, scheduler);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Could not load teleport configuration", failure);
        }
        return create(plugin, combatAdapter, regionAdapter, scheduler, configuration);
    }

    public static DefaultTeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler,
            TeleportMessages messages) {
        return create(plugin, combatAdapter, regionAdapter, scheduler, messages, new TeleportOptionsFactory(), null);
    }

    private static DefaultTeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler,
            TeleportConfiguration configuration) {
        return create(
                plugin,
                combatAdapter,
                regionAdapter,
                scheduler,
                configuration.messages(),
                configuration.options(),
                configuration);
    }

    private static DefaultTeleportModule create(
            Plugin plugin,
            CombatAdapter combatAdapter,
            RegionProtectionAdapter regionAdapter,
            PaperTaskScheduler scheduler,
            TeleportMessages messages,
            TeleportOptionsFactory options,
            @Nullable AutoCloseable configuration) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(combatAdapter, "combatAdapter");
        Objects.requireNonNull(regionAdapter, "regionAdapter");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(options, "options");
        @Nullable Cotani cotani = null;
        try {
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

            TeleportService teleportService = new PaperTeleportService(new PaperTeleportService.Dependencies(
                    policyChain,
                    new DefaultSafeLocationResolver(scheduler),
                    eventNotifier,
                    resultMapper,
                    cooldownService,
                    scheduler,
                    clock));

            DefaultPendingTeleportService pendingTeleportService =
                    new DefaultPendingTeleportService(teleportService, scheduler);

            PendingTeleportListener listener = new PendingTeleportListener(pendingTeleportService);

            cotani = Cotani.forPlugin(plugin)
                    .with(configuration == null ? () -> {} : configuration)
                    .with(cooldownService)
                    .with(pendingTeleportService)
                    .with(() -> unregisterListener(listener))
                    .build();

            PluginManager pluginManager = plugin.getServer().getPluginManager();
            pluginManager.registerEvents(listener, plugin);

            return new DefaultTeleportModule(
                    cotani, scheduler, teleportService, pendingTeleportService, cooldownService, options);
        } catch (RuntimeException failure) {
            if (cotani == null) {
                if (configuration != null) {
                    try {
                        configuration.close();
                    } catch (Exception closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
            } else {
                cotani.close();
            }
            throw new IllegalStateException("Could not initialize teleport module", failure);
        }
    }

    private static void unregisterListener(org.bukkit.event.Listener listener) {
        org.bukkit.event.HandlerList.unregisterAll(listener);
    }

    @Override
    public Cotani cotani() {
        return cotani;
    }

    @Override
    public TeleportService teleportService() {
        return teleportService;
    }

    @Override
    public PendingTeleportService pendingTeleportService() {
        return pendingTeleportService;
    }

    @Override
    public TeleportCooldownService cooldownService() {
        return cooldownService;
    }

    @Override
    public TeleportOptionsFactory options() {
        return options;
    }

    @Override
    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public void close() {
        cotani.close();
    }
}
