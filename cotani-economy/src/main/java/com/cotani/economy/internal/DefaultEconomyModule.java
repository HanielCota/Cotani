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
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("resource")
public final class DefaultEconomyModule implements EconomyModule {

    private final EconomyService service;
    private final Cotani cotani;
    private final boolean ownsCotani;

    private DefaultEconomyModule(EconomyService service, Cotani cotani, boolean ownsCotani) {
        this.service = Objects.requireNonNull(service, "service");
        this.cotani = Objects.requireNonNull(cotani, "cotani");
        this.ownsCotani = ownsCotani;
    }

    public static DefaultEconomyModule create(EconomyModule.Context context) {
        Objects.requireNonNull(context, "context");

        Plugin plugin = context.plugin();
        CotaniStorage storage = context.storage();
        PaperTaskScheduler scheduler = context.scheduler();

        EconomyConfiguration configuration = EconomyConfiguration.load(plugin, scheduler);
        EconomySettings settings = configuration.settings();

        Clock clock = Clock.systemUTC();
        SqlEconomyStore store = new SqlEconomyStore(storage, clock, settings);
        DefaultEconomyGuard guard = new DefaultEconomyGuard(settings);
        EconomyEventPublisher bukkitPublisher = BukkitEconomyEventPublisher.create();
        EconomyEventPublisher mainThreadPublisher =
                new MainThreadEconomyEventPublisher(scheduler, bukkitPublisher, plugin.getLogger());

        DefaultEconomyService coreService =
                new DefaultEconomyService(settings, guard, store, store, mainThreadPublisher);
        CachedEconomyService cachedService = new CachedEconomyService(coreService, scheduler.asyncExecutor(), settings);

        Cotani cotani = resolveCotani(context.cotani(), plugin);
        cotani.register(cachedService);

        return new DefaultEconomyModule(cachedService, cotani, context.cotani() == null);
    }

    private static Cotani resolveCotani(@Nullable Cotani cotani, Plugin plugin) {
        return cotani != null ? cotani : Cotani.forPlugin(plugin).build();
    }

    @Override
    public EconomyService economyService() {
        return service;
    }

    @Override
    public void close() {
        if (service instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new RuntimeException("Failed to close economy service", exception);
            }
        }
        if (ownsCotani) {
            cotani.close();
        }
    }
}
