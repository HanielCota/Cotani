package com.cotani.market.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.market.api.MarketItem;
import com.cotani.market.api.MarketListing;
import com.cotani.market.api.MarketListingId;
import com.cotani.market.api.MarketListingRequest;
import com.cotani.market.api.MarketPage;
import com.cotani.market.api.MarketPrice;
import com.cotani.market.api.MarketPurchase;
import com.cotani.market.api.MarketPurchaseId;
import com.cotani.market.api.MarketPurchasePendingException;
import com.cotani.market.api.MarketPurchaseRequest;
import com.cotani.market.api.MarketPurchaseReservation;
import com.cotani.market.api.MarketPurchaseStateException;
import com.cotani.market.api.MarketPurchaseStatus;
import com.cotani.market.api.MarketQuery;
import com.cotani.market.api.MarketRepository;
import com.cotani.market.api.MarketService;
import com.cotani.market.api.MarketServiceOptions;
import com.cotani.market.api.MarketSettlementService;
import com.cotani.market.api.MarketSettlementStatus;
import com.cotani.market.api.event.ListingCancelledEvent;
import com.cotani.market.api.event.ListingCreatedEvent;
import com.cotani.market.api.event.PurchaseReleasedEvent;
import com.cotani.market.api.event.PurchaseReservedEvent;
import com.cotani.market.api.event.PurchaseSettledEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Coordinates marketplace use cases without serializing unrelated listings or purchases. */
@InternalApi
public final class DefaultMarketService implements MarketService {
    private static final Logger LOGGER = Logger.getLogger(DefaultMarketService.class.getName());

    private final MarketRepository repository;
    private final MarketSettlementService settlementService;
    private final @Nullable EventBus eventBus;
    private final MarketServiceOptions options;
    private final Clock clock;
    private final Object lifecycleLock = new Object();
    private final Map<MarketPurchaseId, CompletionStage<Void>> purchaseTails = new ConcurrentHashMap<>();
    private final Set<CompletionStage<?>> activeOperations = ConcurrentHashMap.newKeySet();
    private boolean closed;
    private @Nullable CompletionStage<Void> closeStage;

    public DefaultMarketService(
            MarketRepository repository,
            MarketSettlementService settlementService,
            @Nullable EventBus eventBus,
            MarketServiceOptions options,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settlementService = Objects.requireNonNull(settlementService, "settlementService");
        this.eventBus = eventBus;
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletionStage<MarketListing> listAsync(MarketListingRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.expiresAt().isAfter(clock.instant())) {
            return failed(new IllegalArgumentException("listing expiration must be in the future"));
        }
        return accept(() -> {
            var durable = repository.createAsync(MarketListing.active(request));
            return options.withRepositoryTimeout(durable).thenApply(listing -> {
                publish(new ListingCreatedEvent(listing), "listing created");
                return listing;
            });
        });
    }

    @Override
    public CompletionStage<MarketListing> listAsync(
            UUID sellerId, MarketItem item, MarketPrice price, Duration duration) {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(price, "price");
        return listAsync(MarketListingRequest.create(sellerId, item, price, duration, clock.instant()));
    }

    @Override
    public CompletionStage<MarketPage> browseAsync(MarketQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.pageSize() > options.maxPageSize()) {
            return failed(new IllegalArgumentException("pageSize exceeds configured maximum"));
        }
        return accept(() -> options.withRepositoryTimeout(repository.browseAsync(query, clock.instant())));
    }

    @Override
    public CompletionStage<Optional<MarketListing>> findAsync(MarketListingId listingId) {
        Objects.requireNonNull(listingId, "listingId");
        return accept(() -> options.withRepositoryTimeout(repository.findAsync(listingId)));
    }

    @Override
    public CompletionStage<MarketListing> cancelAsync(UUID sellerId, MarketListingId listingId) {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(listingId, "listingId");
        return accept(() -> options.withRepositoryTimeout(repository.cancelAsync(sellerId, listingId, clock.instant())))
                .thenApply(listing -> {
                    publish(new ListingCancelledEvent(listing), "listing cancelled");
                    return listing;
                });
    }

    @Override
    public CompletionStage<MarketPurchase> purchaseAsync(MarketPurchaseRequest request) {
        Objects.requireNonNull(request, "request");
        return serializePurchase(request.purchaseId(), () -> {
            var reservation =
                    options.withPurchaseReservationTimeout(repository.reservePurchaseAsync(request, clock.instant()));
            var durable = reservation.thenCompose(this::settleReservation);
            var visible = options.withSettlementTimeout(durable).exceptionallyCompose(failure -> {
                var cause = unwrap(failure);
                if (cause instanceof MarketPurchasePendingException pending) {
                    return failed(pending);
                }
                if (cause instanceof TimeoutException) {
                    return failed(new MarketPurchasePendingException(request.purchaseId(), cause));
                }
                return failed(cause);
            });
            return new PurchaseOperation<>(visible, durable.thenApply(ignored -> null));
        });
    }

    @Override
    public CompletionStage<MarketPurchase> releasePendingAsync(MarketPurchaseId purchaseId) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        return serializePurchase(purchaseId, () -> {
            var durable = repository
                    .findPurchaseAsync(purchaseId)
                    .thenCompose(found -> found.map(this::reconcilePending)
                            .orElseGet(() -> failed(
                                    new IllegalArgumentException("purchase does not exist: " + purchaseId.value()))));
            var visible = options.withSettlementTimeout(durable).thenApply(this::publishReleasedIfNeeded);
            return new PurchaseOperation<>(visible, durable.thenApply(ignored -> null));
        });
    }

    @Override
    public CompletionStage<Optional<MarketPurchase>> findPurchaseAsync(MarketPurchaseId purchaseId) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        return accept(() -> options.withRepositoryTimeout(repository.findPurchaseAsync(purchaseId)));
    }

    @Override
    public CompletionStage<List<MarketPurchase>> pendingPurchasesAsync(int limit) {
        if (limit <= 0 || limit > options.maxPendingRecovery()) {
            return failed(new IllegalArgumentException("limit exceeds configured recovery maximum"));
        }
        return accept(() -> options.withRepositoryTimeout(repository.pendingPurchasesAsync(limit)));
    }

    @Override
    public CompletionStage<Void> purgeAsync(Instant before) {
        Objects.requireNonNull(before, "before");
        return accept(() -> options.withRepositoryTimeout(repository.purgeAsync(before)));
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed = true;
            var accepted = activeOperations.stream()
                    .map(operation ->
                            operation.handle((ignored, failure) -> null).toCompletableFuture())
                    .toArray(CompletableFuture<?>[]::new);
            closeStage = CompletableFuture.allOf(accepted);
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close market service", unwrap(failure));
            }
        });
    }

    private CompletionStage<MarketPurchase> settleReservation(MarketPurchaseReservation reservation) {
        return settleIfNeeded(reservation.purchase(), reservation.created());
    }

    private CompletionStage<MarketPurchase> settleIfNeeded(MarketPurchase purchase, boolean created) {
        if (purchase.status() == MarketPurchaseStatus.SETTLED) {
            return completed(purchase);
        }
        if (purchase.status() == MarketPurchaseStatus.CANCELLED) {
            return failed(new MarketPurchaseStateException(purchase.id(), purchase.status()));
        }
        if (created) {
            publish(new PurchaseReservedEvent(purchase), "purchase reserved");
        }
        CompletionStage<Void> settlement;
        try {
            settlement = Objects.requireNonNull(settlementService.settleAsync(purchase), "settlement stage");
        } catch (RuntimeException failure) {
            return failed(new MarketPurchasePendingException(purchase.id(), failure));
        }
        return settlement
                .thenCompose(ignored -> repository.markSettledAsync(purchase.id(), clock.instant()))
                .thenCompose(marked -> marked
                        ? repository
                                .findPurchaseAsync(purchase.id())
                                .thenCompose(found -> found.map(this::publishSettled)
                                        .map(DefaultMarketService::completed)
                                        .orElseGet(() -> failed(new MarketPurchasePendingException(
                                                purchase.id(),
                                                new IllegalStateException("settled purchase disappeared")))))
                        : failed(new MarketPurchasePendingException(
                                purchase.id(), new IllegalStateException("purchase was not marked settled"))))
                .exceptionallyCompose(failure -> {
                    var cause = unwrap(failure);
                    return failed(
                            cause instanceof MarketPurchasePendingException
                                    ? cause
                                    : new MarketPurchasePendingException(purchase.id(), cause));
                });
    }

    private CompletionStage<MarketPurchase> reconcilePending(MarketPurchase purchase) {
        if (purchase.status() != MarketPurchaseStatus.PENDING) {
            return completed(purchase);
        }
        final CompletionStage<MarketSettlementStatus> status;
        try {
            status = Objects.requireNonNull(settlementService.statusAsync(purchase), "settlement status stage");
        } catch (RuntimeException failure) {
            return failed(new MarketPurchasePendingException(purchase.id(), failure));
        }
        return status.thenCompose(settlementStatus -> switch (settlementStatus) {
                    case NOT_STARTED, FAILED -> releaseAndFind(purchase);
                    case SETTLED -> markSettledAndFind(purchase);
                    case IN_PROGRESS, UNKNOWN ->
                        failed(new MarketPurchasePendingException(
                                purchase.id(), new IllegalStateException("settlement status is " + settlementStatus)));
                })
                .exceptionallyCompose(failure -> {
                    var cause = unwrap(failure);
                    return failed(
                            cause instanceof MarketPurchasePendingException
                                    ? cause
                                    : new MarketPurchasePendingException(purchase.id(), cause));
                });
    }

    private CompletionStage<MarketPurchase> releaseAndFind(MarketPurchase purchase) {
        return repository
                .releasePendingAsync(purchase.id(), clock.instant())
                .thenCompose(released -> released
                        ? repository
                                .findPurchaseAsync(purchase.id())
                                .thenCompose(found -> found.map(DefaultMarketService::completed)
                                        .orElseGet(() -> failed(new MarketPurchasePendingException(
                                                purchase.id(),
                                                new IllegalStateException("released purchase disappeared")))))
                        : failed(new MarketPurchasePendingException(
                                purchase.id(), new IllegalStateException("purchase was not released"))));
    }

    private CompletionStage<MarketPurchase> markSettledAndFind(MarketPurchase purchase) {
        return repository
                .markSettledAsync(purchase.id(), clock.instant())
                .thenCompose(marked -> marked
                        ? repository
                                .findPurchaseAsync(purchase.id())
                                .thenCompose(found -> found.map(this::publishSettled)
                                        .map(DefaultMarketService::completed)
                                        .orElseGet(() -> failed(new MarketPurchasePendingException(
                                                purchase.id(),
                                                new IllegalStateException("settled purchase disappeared")))))
                        : failed(new MarketPurchasePendingException(
                                purchase.id(), new IllegalStateException("purchase was not marked settled"))));
    }

    private MarketPurchase publishReleasedIfNeeded(MarketPurchase purchase) {
        if (purchase.status() == MarketPurchaseStatus.CANCELLED) {
            publish(new PurchaseReleasedEvent(purchase), "purchase released");
        }
        return purchase;
    }

    private MarketPurchase publishSettled(MarketPurchase purchase) {
        publish(new PurchaseSettledEvent(purchase), "purchase settled");
        return purchase;
    }

    private <T> CompletionStage<T> serializePurchase(
            MarketPurchaseId purchaseId, Supplier<PurchaseOperation<T>> operation) {
        synchronized (purchaseTails) {
            var predecessor = purchaseTails.getOrDefault(purchaseId, completedVoid());
            var flow = predecessor.handle((ignored, failure) -> null).thenCompose(ignored -> {
                try {
                    return completed(operation.get());
                } catch (RuntimeException failure) {
                    return failed(failure);
                }
            });
            var visible = flow.thenCompose(PurchaseOperation::visible);
            CompletionStage<Void> tail =
                    flow.thenCompose(PurchaseOperation::durable).handle((ignored, failure) -> (Void) null);
            purchaseTails.put(purchaseId, tail);
            tail.whenComplete((ignored, failure) -> purchaseTails.remove(purchaseId, tail));
            return accept(() -> visible);
        }
    }

    private <T> CompletionStage<T> accept(Supplier<CompletionStage<T>> operation) {
        synchronized (lifecycleLock) {
            if (closed) {
                return failed(new IllegalStateException("Market service is closed"));
            }
            final CompletionStage<T> stage;
            try {
                stage = Objects.requireNonNull(operation.get(), "operation stage");
            } catch (RuntimeException failure) {
                return failed(failure);
            }
            activeOperations.add(stage);
            stage.whenComplete((ignored, failure) -> activeOperations.remove(stage));
            return stage;
        }
    }

    private void publish(com.cotani.event.api.CotaniEvent event, String description) {
        if (eventBus == null) {
            return;
        }
        try {
            var publication = Objects.requireNonNull(eventBus.publishAsync(event), "event bus stage");
            options.withRepositoryTimeout(publication).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.log(Level.WARNING, "Failed to publish market " + description + " event", unwrap(failure));
                }
            });
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to publish market " + description + " event", failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
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

    private record PurchaseOperation<T>(CompletionStage<T> visible, CompletionStage<Void> durable) {
        private PurchaseOperation {
            Objects.requireNonNull(visible, "visible");
            Objects.requireNonNull(durable, "durable");
        }
    }
}
