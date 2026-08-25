package com.cotani.trade.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.trade.api.TradeAccessDeniedException;
import com.cotani.trade.api.TradeAsset;
import com.cotani.trade.api.TradeConflictException;
import com.cotani.trade.api.TradeId;
import com.cotani.trade.api.TradeNotFoundException;
import com.cotani.trade.api.TradeOffer;
import com.cotani.trade.api.TradeOptions;
import com.cotani.trade.api.TradeRepository;
import com.cotani.trade.api.TradeService;
import com.cotani.trade.api.TradeServiceOptions;
import com.cotani.trade.api.TradeSession;
import com.cotani.trade.api.TradeSettlement;
import com.cotani.trade.api.TradeSettlementFailedException;
import com.cotani.trade.api.TradeSettlementPendingException;
import com.cotani.trade.api.TradeSettlementService;
import com.cotani.trade.api.TradeSettlementStatus;
import com.cotani.trade.api.TradeStateException;
import com.cotani.trade.api.TradeStatus;
import com.cotani.trade.api.TradeTimeoutScheduler;
import com.cotani.trade.api.event.TradeCancelledEvent;
import com.cotani.trade.api.event.TradeCompletedEvent;
import com.cotani.trade.api.event.TradeConfirmedEvent;
import com.cotani.trade.api.event.TradeCreatedEvent;
import com.cotani.trade.api.event.TradeEvent;
import com.cotani.trade.api.event.TradeExpiredEvent;
import com.cotani.trade.api.event.TradeFailedEvent;
import com.cotani.trade.api.event.TradeOfferChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultTradeService implements TradeService {
    private static final Logger LOGGER = Logger.getLogger(DefaultTradeService.class.getName());

    private final Object stateLock = new Object();
    private final Map<TradeId, TradeSession> trades = new LinkedHashMap<>();
    private final @Nullable TradeRepository repository;
    private final @Nullable EventBus eventBus;
    private final TradeSettlementService settlementService;
    private final TradeServiceOptions options;
    private final TradeTimeoutScheduler timeoutScheduler;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();

    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    public DefaultTradeService(
            List<TradeSession> initialTrades,
            @Nullable TradeRepository repository,
            @Nullable EventBus eventBus,
            TradeSettlementService settlementService,
            TradeServiceOptions options,
            Clock clock) {
        this(
                initialTrades,
                repository,
                eventBus,
                settlementService,
                options,
                new ExecutorTradeTimeoutScheduler(),
                clock);
    }

    public DefaultTradeService(
            List<TradeSession> initialTrades,
            @Nullable TradeRepository repository,
            @Nullable EventBus eventBus,
            TradeSettlementService settlementService,
            TradeServiceOptions options,
            TradeTimeoutScheduler timeoutScheduler,
            Clock clock) {
        Objects.requireNonNull(initialTrades, "initialTrades");
        Objects.requireNonNull(settlementService, "settlementService");
        this.repository = repository;
        this.eventBus = eventBus;
        this.settlementService = settlementService;
        this.options = Objects.requireNonNull(options, "options");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        loadInitialTrades(initialTrades);
    }

    /** Reconciles expired open trades and pending settlements after repository loading. */
    public CompletionStage<Void> initializeAsync() {
        return submit(this::reconcileInitialState);
    }

    @Override
    public CompletionStage<TradeSession> createAsync(UUID initiatorId, UUID recipientId, TradeOptions tradeOptions) {
        Objects.requireNonNull(initiatorId, "initiatorId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(tradeOptions, "tradeOptions");
        if (initiatorId.equals(recipientId)) {
            return failed(new IllegalArgumentException("initiator and recipient must differ"));
        }
        return submit(() -> {
            var now = clock.instant();
            if (hasActiveTrade(initiatorId, now) || hasActiveTrade(recipientId, now)) {
                throw new TradeConflictException("a participant already has an active trade");
            }
            var trade = TradeSession.create(TradeId.random(), initiatorId, recipientId, tradeOptions, now);
            return persistCreate(trade)
                    .thenRun(() -> store(trade))
                    .thenCompose(ignored -> publish(new TradeCreatedEvent(trade)))
                    .thenApply(ignored -> trade);
        });
    }

    @Override
    public CompletionStage<TradeSession> offerAsync(TradeId tradeId, UUID playerId, List<TradeAsset> assets) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(playerId, "playerId");
        var requestedOffer = new TradeOffer(playerId, Objects.requireNonNull(assets, "assets"));
        validateOfferLimits(requestedOffer);
        return submit(() -> {
            var current = requireTrade(tradeId);
            requireParticipant(current, playerId);
            return requireOpenAsync(current).thenCompose(openTrade -> {
                var updated = openTrade.withOffer(playerId, requestedOffer.assets());
                return update(openTrade, updated)
                        .thenCompose(ignored -> publish(new TradeOfferChangedEvent(updated)))
                        .thenApply(ignored -> updated);
            });
        });
    }

    @Override
    public CompletionStage<TradeSession> confirmAsync(TradeId tradeId, UUID playerId) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> {
            var current = requireTrade(tradeId);
            requireParticipant(current, playerId);
            if (current.status() != TradeStatus.OPEN || current.isExpiredAt(clock.instant())) {
                return requireOpenAsync(current);
            }
            if (current.confirmations().contains(playerId)) {
                return completed(current);
            }
            var confirmed = current.withConfirmation(playerId);
            if (!confirmed.bothConfirmed()) {
                return update(current, confirmed)
                        .thenCompose(ignored -> publish(new TradeConfirmedEvent(confirmed)))
                        .thenApply(ignored -> confirmed);
            }
            var pending = confirmed.withStatus(TradeStatus.SETTLEMENT_PENDING);
            return update(current, confirmed)
                    .thenCompose(ignored -> publish(new TradeConfirmedEvent(confirmed)))
                    .thenCompose(ignored -> update(confirmed, pending))
                    .thenCompose(ignored -> settleAndFinish(pending));
        });
    }

    @Override
    public CompletionStage<TradeSession> cancelAsync(TradeId tradeId, UUID playerId) {
        Objects.requireNonNull(tradeId, "tradeId");
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> {
            var current = requireTrade(tradeId);
            requireParticipant(current, playerId);
            return requireOpenAsync(current).thenCompose(openTrade -> {
                var cancelled = openTrade.withStatus(TradeStatus.CANCELLED);
                return update(openTrade, cancelled)
                        .thenCompose(ignored -> publish(new TradeCancelledEvent(cancelled)))
                        .thenApply(ignored -> cancelled);
            });
        });
    }

    @Override
    public CompletionStage<Optional<TradeSession>> findAsync(TradeId tradeId) {
        Objects.requireNonNull(tradeId, "tradeId");
        return submit(() -> completed(find(tradeId)));
    }

    @Override
    public CompletionStage<Optional<TradeSession>> findByPlayerAsync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> {
            var now = clock.instant();
            synchronized (stateLock) {
                return completed(trades.values().stream()
                        .filter(trade -> trade.contains(playerId) && trade.isActiveAt(now))
                        .findFirst());
            }
        });
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (stateLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage = lastOperation
                    .handle((ignored, failure) -> null)
                    .thenCompose(ignored -> timeoutScheduler.closeAsync())
                    .whenComplete((ignored, failure) -> {
                        synchronized (stateLock) {
                            trades.clear();
                        }
                    });
            return closeStage;
        }
    }

    private void loadInitialTrades(List<TradeSession> initialTrades) {
        var activeParticipants = new HashSet<UUID>();
        var now = clock.instant();
        for (var trade : initialTrades) {
            var value = Objects.requireNonNull(trade, "trade");
            if (trades.put(value.id(), value) != null) {
                throw new IllegalArgumentException("initial trades contain duplicate ids");
            }
            validateOfferLimits(value.initiatorOffer());
            validateOfferLimits(value.recipientOffer());
            if (!value.isActiveAt(now)) {
                continue;
            }
            if (!activeParticipants.add(value.initiatorId()) || !activeParticipants.add(value.recipientId())) {
                throw new IllegalArgumentException("initial trades contain duplicate active participants");
            }
        }
    }

    private CompletionStage<Void> reconcileInitialState() {
        List<TradeSession> loaded;
        synchronized (stateLock) {
            loaded = new ArrayList<>(trades.values());
        }
        CompletionStage<Void> sequence = completedVoid();
        for (var trade : loaded) {
            if (trade.status() == TradeStatus.OPEN && trade.isExpiredAt(clock.instant())) {
                sequence = sequence.thenCompose(ignored -> expireLoadedTrade(trade));
            }
        }
        for (var trade : loaded) {
            if (trade.status() == TradeStatus.SETTLEMENT_PENDING) {
                sequence = sequence.thenCompose(ignored -> reconcilePendingTrade(trade));
            }
        }
        return sequence;
    }

    private CompletionStage<Void> expireLoadedTrade(TradeSession loaded) {
        var current = currentTrade(loaded.id());
        if (current.isEmpty() || current.orElseThrow().status() != TradeStatus.OPEN) {
            return completedVoid();
        }
        var expired = current.orElseThrow().withStatus(TradeStatus.EXPIRED);
        return update(current.orElseThrow(), expired).thenCompose(ignored -> publish(new TradeExpiredEvent(expired)));
    }

    private CompletionStage<Void> reconcilePendingTrade(TradeSession loaded) {
        var current = currentTrade(loaded.id());
        if (current.isEmpty() || current.orElseThrow().status() != TradeStatus.SETTLEMENT_PENDING) {
            return completedVoid();
        }
        var pending = current.orElseThrow();
        CompletionStage<TradeSettlementStatus> statusStage;
        try {
            statusStage =
                    Objects.requireNonNull(settlementService.statusAsync(pending.id()), "settlement status stage");
        } catch (RuntimeException failure) {
            logRecoveryFailure(pending.id(), failure);
            return completedVoid();
        }
        return timeoutScheduler
                .withTimeout(statusStage, options.settlementTimeout(), "settlement status")
                .handle((status, failure) -> {
                    if (failure != null) {
                        logRecoveryFailure(pending.id(), failure);
                        return TradeSettlementStatus.UNKNOWN;
                    }
                    return Objects.requireNonNull(status, "settlement status");
                })
                .thenCompose(status -> reconcilePendingStatus(pending, status));
    }

    private CompletionStage<Void> reconcilePendingStatus(TradeSession pending, TradeSettlementStatus status) {
        if (status == TradeSettlementStatus.COMPLETED) {
            return completeSettlement(pending).thenApply(ignored -> null);
        }
        if (status == TradeSettlementStatus.FAILED) {
            return failSettlement(pending, new TradeSettlementFailedException(pending.id()))
                    .handle((ignored, failure) -> null);
        }
        if (status == TradeSettlementStatus.NOT_STARTED) {
            return settleAndFinish(pending).handle((ignored, failure) -> null);
        }
        return completedVoid();
    }

    private CompletionStage<TradeSession> settleAndFinish(TradeSession pending) {
        var settlement = new TradeSettlement(pending.id(), pending.initiatorOffer(), pending.recipientOffer());
        CompletionStage<Void> source;
        try {
            source = Objects.requireNonNull(settlementService.settleAsync(settlement), "settlement stage");
        } catch (RuntimeException failure) {
            return failSettlement(pending, failure);
        }
        var timedOut = new AtomicBoolean();
        var sourceSucceeded = new AtomicBoolean();
        var recoveryQueued = new AtomicBoolean();
        monitorLateSettlement(pending, source, timedOut, sourceSucceeded, recoveryQueued);
        CompletionStage<Void> timed;
        try {
            timed = timeoutScheduler.withTimeout(source, options.settlementTimeout(), "settlement");
        } catch (RuntimeException failure) {
            timedOut.set(true);
            if (sourceSucceeded.get()) {
                queueLateSettlementRecovery(pending, recoveryQueued);
            }
            return failed(new TradeSettlementPendingException(pending.id()));
        }
        return timed.handle((ignored, failure) -> failure).thenCompose(failure -> {
            if (failure == null) {
                return completeSettlement(pending);
            }
            if (isTimeout(failure)) {
                timedOut.set(true);
                if (sourceSucceeded.get()) {
                    queueLateSettlementRecovery(pending, recoveryQueued);
                }
                return failed(new TradeSettlementPendingException(pending.id()));
            }
            return failSettlement(pending, unwrap(failure));
        });
    }

    private void monitorLateSettlement(
            TradeSession pending,
            CompletionStage<Void> source,
            AtomicBoolean timedOut,
            AtomicBoolean sourceSucceeded,
            AtomicBoolean recoveryQueued) {
        try {
            Objects.requireNonNull(
                    source.whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            return;
                        }
                        sourceSucceeded.set(true);
                        if (timedOut.get()) {
                            queueLateSettlementRecovery(pending, recoveryQueued);
                        }
                    }),
                    "settlement completion stage");
        } catch (RuntimeException failure) {
            logRecoveryFailure(pending.id(), failure);
        }
    }

    private void queueLateSettlementRecovery(TradeSession pending, AtomicBoolean recoveryQueued) {
        if (!recoveryQueued.compareAndSet(false, true)) {
            return;
        }
        submit(() -> completePendingAfterLateSettlement(pending)).whenComplete((ignoredCompletion, recoveryFailure) -> {
            if (recoveryFailure != null) {
                logRecoveryFailure(pending.id(), recoveryFailure);
            }
        });
    }

    private CompletionStage<Void> completePendingAfterLateSettlement(TradeSession pending) {
        var current = currentTrade(pending.id());
        if (current.isEmpty()) {
            return completedVoid();
        }
        var currentTrade = current.orElseThrow();
        if (currentTrade.status() != TradeStatus.SETTLEMENT_PENDING || currentTrade.revision() != pending.revision()) {
            return completedVoid();
        }
        return completeSettlement(currentTrade).thenApply(ignored -> null);
    }

    private CompletionStage<TradeSession> completeSettlement(TradeSession pending) {
        var completed = pending.withStatus(TradeStatus.COMPLETED);
        return update(pending, completed)
                .thenCompose(ignored -> publish(new TradeCompletedEvent(completed)))
                .thenApply(ignored -> completed);
    }

    private CompletionStage<TradeSession> failSettlement(TradeSession pending, Throwable failure) {
        var failed = pending.withStatus(TradeStatus.FAILED);
        return update(pending, failed)
                .thenCompose(ignored -> publish(new TradeFailedEvent(failed)))
                .thenCompose(ignored -> DefaultTradeService.<TradeSession>failed(unwrap(failure)));
    }

    private CompletionStage<Void> persistCreate(TradeSession trade) {
        if (repository == null) {
            return completedVoid();
        }
        return timeoutScheduler.withTimeout(
                Objects.requireNonNull(repository.createAsync(trade), "repository create stage"),
                options.repositoryTimeout(),
                "repository create");
    }

    private CompletionStage<Void> update(TradeSession current, TradeSession updated) {
        if (!current.id().equals(updated.id()) || updated.revision() != current.revision() + 1) {
            return failed(new IllegalArgumentException("trade update must advance exactly one revision"));
        }
        CompletionStage<Void> persisted = completedVoid();
        if (repository != null) {
            persisted = timeoutScheduler.withTimeout(
                    Objects.requireNonNull(
                            repository.updateAsync(updated.id(), current.revision(), updated),
                            "repository update stage"),
                    options.repositoryTimeout(),
                    "repository update");
        }
        return persisted.thenRun(() -> store(updated));
    }

    private CompletionStage<Void> publish(TradeEvent event) {
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
                            LOGGER.log(
                                    Level.WARNING,
                                    "Trade event publication failed: "
                                            + event.getClass().getName(),
                                    failure);
                        }
                        return null;
                    });
        } catch (RuntimeException failure) {
            LOGGER.log(
                    Level.WARNING,
                    "Trade event publication failed: " + event.getClass().getName(),
                    failure);
            return completedVoid();
        }
    }

    private TradeSession requireTrade(TradeId tradeId) {
        return currentTrade(tradeId).orElseThrow(() -> new TradeNotFoundException(tradeId));
    }

    private Optional<TradeSession> currentTrade(TradeId tradeId) {
        synchronized (stateLock) {
            return Optional.ofNullable(trades.get(tradeId));
        }
    }

    private Optional<TradeSession> find(TradeId tradeId) {
        return currentTrade(tradeId);
    }

    private boolean hasActiveTrade(UUID playerId, Instant now) {
        synchronized (stateLock) {
            return trades.values().stream().anyMatch(trade -> trade.contains(playerId) && trade.isActiveAt(now));
        }
    }

    private static void requireParticipant(TradeSession trade, UUID playerId) {
        if (!trade.contains(playerId)) {
            throw new TradeAccessDeniedException(trade.id());
        }
    }

    private CompletionStage<TradeSession> requireOpenAsync(TradeSession trade) {
        if (trade.status() != TradeStatus.OPEN) {
            return failed(new TradeStateException(trade.id(), trade.status()));
        }
        if (!trade.isExpiredAt(clock.instant())) {
            return completed(trade);
        }
        var expired = trade.withStatus(TradeStatus.EXPIRED);
        return update(trade, expired)
                .thenCompose(ignored -> publish(new TradeExpiredEvent(expired)))
                .thenCompose(ignored -> failed(new TradeStateException(trade.id(), TradeStatus.EXPIRED)));
    }

    private void validateOfferLimits(TradeOffer offer) {
        if (offer.assets().size() > options.maximumAssetsPerParticipant()) {
            throw new IllegalArgumentException("offer exceeds the configured asset limit");
        }
        if (offer.encodedSizeBytes() > options.maximumEncodedBytesPerParticipant()) {
            throw new IllegalArgumentException("offer exceeds the configured encoded byte limit");
        }
    }

    private void store(TradeSession trade) {
        synchronized (stateLock) {
            trades.put(trade.id(), trade);
            trimTerminalHistory();
        }
    }

    private void trimTerminalHistory() {
        var terminalCount =
                trades.values().stream().filter(DefaultTradeService::isTerminal).count();
        if (terminalCount <= options.maximumRetainedTerminalTrades()) {
            return;
        }
        var iterator = trades.entrySet().iterator();
        while (terminalCount > options.maximumRetainedTerminalTrades() && iterator.hasNext()) {
            var entry = iterator.next();
            if (!isTerminal(entry.getValue())) {
                continue;
            }
            iterator.remove();
            terminalCount--;
        }
    }

    private static boolean isTerminal(TradeSession trade) {
        return switch (trade.status()) {
            case COMPLETED, CANCELLED, EXPIRED, FAILED -> true;
            case OPEN, SETTLEMENT_PENDING -> false;
        };
    }

    private <T> CompletionStage<T> submit(Supplier<CompletionStage<T>> operation) {
        synchronized (stateLock) {
            if (closed.get()) {
                return failed(new IllegalStateException("Trade service is closed"));
            }
            var previous = sequencingTail;
            var submitted = new CompletableFuture<T>();
            sequencingTail = submitted.handle((ignored, failure) -> null);
            lastOperation = sequencingTail;
            previous.handle((ignored, failure) -> null)
                    .thenCompose(ignored -> {
                        try {
                            return Objects.requireNonNull(operation.get(), "operation stage");
                        } catch (RuntimeException failure) {
                            return failed(failure);
                        }
                    })
                    .whenComplete((value, failure) -> {
                        if (failure != null) {
                            submitted.completeExceptionally(failure);
                            return;
                        }
                        submitted.complete(value);
                    });
            return submitted;
        }
    }

    private static boolean isTimeout(Throwable failure) {
        return unwrap(failure) instanceof TimeoutException;
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void logRecoveryFailure(TradeId tradeId, Throwable failure) {
        LOGGER.log(Level.WARNING, "Trade settlement recovery failed for " + tradeId, failure);
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }
}
