package com.cotani.economy.internal;

import com.cotani.Cotani;
import com.cotani.economy.EconomyService;
import com.cotani.economy.EconomySettings;
import com.cotani.economy.api.EconomyModule;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.internal.cache.CachedEconomyService;
import com.cotani.economy.internal.config.EconomyConfiguration;
import com.cotani.economy.internal.event.BukkitEconomyEventPublisher;
import com.cotani.economy.internal.event.MainThreadEconomyEventPublisher;
import com.cotani.economy.internal.protection.DefaultEconomyGuard;
import com.cotani.economy.internal.service.DefaultEconomyService;
import com.cotani.economy.internal.storage.SqlEconomyStore;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.plugin.Plugin;

public final class DefaultEconomyModule implements EconomyModule {

    public static DefaultEconomyModule create(EconomyModule.Context context) {
        Objects.requireNonNull(context, "context");

        Plugin plugin = context.plugin();
        CotaniStorage storage = context.storage();
        PaperTaskScheduler scheduler = context.scheduler();

        if (storage.backend() == null) {
            throw new IllegalStateException("Economy module requires a configured storage backend.");
        }

        EconomyConfiguration configuration = EconomyConfiguration.load(plugin, scheduler);
        EconomySettings settings = configuration.settings();

        Clock clock = Clock.systemUTC();
        SqlEconomyStore store = new SqlEconomyStore(storage, clock, settings);
        DefaultEconomyGuard guard = new DefaultEconomyGuard(settings);
        EconomyEventPublisher bukkitPublisher = BukkitEconomyEventPublisher.create();
        EconomyEventPublisher mainThreadPublisher = new MainThreadEconomyEventPublisher(scheduler, bukkitPublisher);

        DefaultEconomyService coreService =
                new DefaultEconomyService(settings, guard, store, store, mainThreadPublisher);
        CachedEconomyService cachedService = new CachedEconomyService(coreService, scheduler, settings);

        Cotani cotani = Optional.ofNullable(context.cotani())
                .orElseGet(() -> Cotani.forPlugin(plugin).build());
        cotani.register(cachedService);

        return new DefaultEconomyModule(cachedService, cotani);
    }

    private DefaultEconomyModule(EconomyService service, Cotani cotani) {
        this.service = Objects.requireNonNull(service, "service");
        this.cotani = Objects.requireNonNull(cotani, "cotani");
    }

    private final EconomyService service;
    private final Cotani cotani;

    @Override
    public com.cotani.economy.EconomyService economyService() {
        return service;
    }

    @Override
    public void close() {
        cotani.close();
    }
}
