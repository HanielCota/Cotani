package com.cotani.trade.internal;

import com.cotani.event.api.EventBus;
import com.cotani.trade.api.TradeRepository;
import com.cotani.trade.api.TradeServiceOptions;
import com.cotani.trade.api.TradeSession;
import com.cotani.trade.api.TradeTimeoutScheduler;
import com.cotani.trade.api.event.TradeEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Coordinates optional persistence and best-effort event publication for trades. */
final class TradePersistenceCoordinator {

    private static final Logger LOGGER = Logger.getLogger(TradePersistenceCoordinator.class.getName());

    private final @Nullable TradeRepository repository;
    private final @Nullable EventBus eventBus;
    private final TradeServiceOptions options;
    private final TradeTimeoutScheduler timeoutScheduler;

    TradePersistenceCoordinator(
            @Nullable TradeRepository repository,
            @Nullable EventBus eventBus,
            TradeServiceOptions options,
            TradeTimeoutScheduler timeoutScheduler) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.options = Objects.requireNonNull(options, "options");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
    }

    CompletionStage<Void> createAsync(TradeSession trade) {
        if (repository == null) {
            return completedVoid();
        }
        return timeoutScheduler.withTimeout(
                Objects.requireNonNull(repository.createAsync(trade), "repository create stage"),
                options.repositoryTimeout(),
                "repository create");
    }

    CompletionStage<Void> updateAsync(TradeSession current, TradeSession updated) {
        if (!current.id().equals(updated.id()) || updated.revision() != current.revision() + 1) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("trade update must advance exactly one revision"));
        }
        if (repository == null) {
            return completedVoid();
        }
        return timeoutScheduler.withTimeout(
                Objects.requireNonNull(
                        repository.updateAsync(updated.id(), current.revision(), updated), "repository update stage"),
                options.repositoryTimeout(),
                "repository update");
    }

    CompletionStage<Void> publishAsync(TradeEvent event) {
        if (eventBus == null) {
            return completedVoid();
        }
        try {
            return timeoutScheduler
                    .withTimeout(
                            Objects.requireNonNull(eventBus.publishAsync(event), "event stage"),
                            options.eventTimeout(),
                            "event")
                    .handle((ignored, failure) -> {
                        if (failure != null) {
                            logPublicationFailure(event, failure);
                        }
                        return null;
                    });
        } catch (RuntimeException failure) {
            logPublicationFailure(event, failure);
            return completedVoid();
        }
    }

    private static void logPublicationFailure(TradeEvent event, Throwable failure) {
        LOGGER.log(
                Level.WARNING,
                "Trade event publication failed: " + event.getClass().getName(),
                failure);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }
}
