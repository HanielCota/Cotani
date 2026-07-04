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
public final class EconomyBootstrap implements AutoCloseable {

    private final EconomyService service;
    private final Runnable closeAction;

    private EconomyBootstrap(EconomyService service, Runnable closeAction) {
        this.service = Objects.requireNonNull(service, "service");
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
    }

    public static EconomyBootstrap createDefault() {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var currency = EconomyCurrency.coins();
        var settings = EconomySettings.defaultSettings(currency);
        var guard = new DefaultEconomyGuard(settings);
        var store = new InMemoryEconomyStore(executor, Clock.systemUTC(), settings);
        var publisher = new NoopEconomyEventPublisher();
        var service = new DefaultEconomyService(settings, guard, store, store, publisher);

        return new EconomyBootstrap(service, executor::close);
    }

    public static EconomyBootstrap create(
            EconomySettings settings, EconomyEventPublisher eventPublisher, Executor executor) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(eventPublisher, "eventPublisher");
        Objects.requireNonNull(executor, "executor");

        var guard = new DefaultEconomyGuard(settings);
        var store = new InMemoryEconomyStore(executor, Clock.systemUTC(), settings);
        var service = new DefaultEconomyService(settings, guard, store, store, eventPublisher);

        return new EconomyBootstrap(service, () -> {});
    }

    public EconomyService service() {
        return service;
    }

    @Override
    public void close() {
        closeAction.run();
        if (service instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new RuntimeException("Failed to close economy service", exception);
            }
        }
    }
}
