package com.cotani.economy;

import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.internal.event.NoopEconomyEventPublisher;
import com.cotani.economy.internal.protection.DefaultEconomyGuard;
import com.cotani.economy.internal.repository.InMemoryEconomyStore;
import com.cotani.economy.internal.service.DefaultEconomyService;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Minimal bootstrap facade for the economy module.
 *
 * <p>In Cotani's real bootstrap, wire this module through the existing service registry instead of using this factory directly.
 */
public final class EconomyModule implements AutoCloseable {

    private final EconomyService service;
    private final Runnable closeAction;

    private EconomyModule(EconomyService service, Runnable closeAction) {
        this.service = Objects.requireNonNull(service, "service");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    public static EconomyModule createDefault() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var currency = EconomyCurrency.coins();
        var settings = EconomySettings.defaultSettings(currency);
        var guard = new DefaultEconomyGuard(settings);
        var store = new InMemoryEconomyStore(executor, Clock.systemUTC(), settings);
        var publisher = new NoopEconomyEventPublisher();
        var service = new DefaultEconomyService(settings, guard, store, store, publisher);

        return new EconomyModule(service, executor::close);
    }

    public static EconomyModule create(
            EconomySettings settings, EconomyEventPublisher eventPublisher, Executor executor) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(eventPublisher, "eventPublisher");
        Objects.requireNonNull(executor, "executor");

        var guard = new DefaultEconomyGuard(settings);
        var store = new InMemoryEconomyStore(executor, Clock.systemUTC(), settings);
        var service = new DefaultEconomyService(settings, guard, store, store, eventPublisher);

        return new EconomyModule(service, () -> {});
    }

    public EconomyService service() {
        return service;
    }

    @Override
    public void close() {
        closeAction.run();
    }
}
