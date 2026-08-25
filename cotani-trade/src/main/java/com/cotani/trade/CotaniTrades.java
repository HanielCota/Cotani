package com.cotani.trade;

import com.cotani.event.api.EventBus;
import com.cotani.trade.api.TradeRepository;
import com.cotani.trade.api.TradeService;
import com.cotani.trade.api.TradeServiceOptions;
import com.cotani.trade.api.TradeSettlementService;
import com.cotani.trade.api.TradeSnapshot;
import com.cotani.trade.api.TradeTimeoutScheduler;
import com.cotani.trade.internal.DefaultTradeService;
import com.cotani.trade.internal.ExecutorTradeTimeoutScheduler;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** Factories for the {@code cotani-trade} module. */
public final class CotaniTrades {
    private CotaniTrades() {}

    /** Creates an in-memory trade service with an explicit settlement adapter. */
    public static TradeService inMemory(TradeSettlementService settlementService) {
        return inMemory(settlementService, null, TradeServiceOptions.defaults());
    }

    /** Creates an in-memory trade service with optional event publication and explicit options. */
    public static TradeService inMemory(
            TradeSettlementService settlementService, @Nullable EventBus eventBus, TradeServiceOptions options) {
        var timeoutScheduler = new ExecutorTradeTimeoutScheduler();
        try {
            return create(
                    TradeSnapshot.empty(),
                    null,
                    eventBus,
                    settlementService,
                    options,
                    timeoutScheduler,
                    Clock.systemUTC());
        } catch (RuntimeException failure) {
            timeoutScheduler.closeAsync();
            throw failure;
        }
    }

    /** Restores trade sessions asynchronously from a repository and reconciles pending settlements. */
    public static CompletionStage<TradeService> fromRepositoryAsync(
            TradeRepository repository, TradeSettlementService settlementService) {
        return fromRepositoryAsync(repository, settlementService, null, TradeServiceOptions.defaults());
    }

    /** Restores trade sessions with optional events and explicit options. */
    public static CompletionStage<TradeService> fromRepositoryAsync(
            TradeRepository repository,
            TradeSettlementService settlementService,
            @Nullable EventBus eventBus,
            TradeServiceOptions options) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(settlementService, "settlementService");
        Objects.requireNonNull(options, "options");
        var timeoutScheduler = new ExecutorTradeTimeoutScheduler();
        CompletionStage<TradeSnapshot> loadStage;
        try {
            loadStage = Objects.requireNonNull(repository.loadAsync(), "repository load stage");
        } catch (RuntimeException failure) {
            timeoutScheduler.closeAsync();
            return CompletableFuture.failedFuture(failure);
        }
        return timeoutScheduler
                .withTimeout(loadStage, options.repositoryTimeout(), "repository load")
                .thenCompose(snapshot -> {
                    DefaultTradeService service;
                    try {
                        service = create(
                                snapshot,
                                repository,
                                eventBus,
                                settlementService,
                                options,
                                timeoutScheduler,
                                Clock.systemUTC());
                    } catch (RuntimeException failure) {
                        timeoutScheduler.closeAsync();
                        return CompletableFuture.failedFuture(failure);
                    }
                    return service.initializeAsync()
                            .thenApply(ignored -> (TradeService) service)
                            .whenComplete((ignored, failure) -> {
                                if (failure != null) {
                                    service.closeAsync();
                                }
                            });
                })
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        timeoutScheduler.closeAsync();
                    }
                });
    }

    private static DefaultTradeService create(
            TradeSnapshot snapshot,
            @Nullable TradeRepository repository,
            @Nullable EventBus eventBus,
            TradeSettlementService settlementService,
            TradeServiceOptions options,
            TradeTimeoutScheduler timeoutScheduler,
            Clock clock) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new DefaultTradeService(
                snapshot.trades(), repository, eventBus, settlementService, options, timeoutScheduler, clock);
    }
}
