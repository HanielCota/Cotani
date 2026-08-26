package com.cotani.market.storage;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.market.api.MarketConflictException;
import com.cotani.market.api.MarketListing;
import com.cotani.market.api.MarketListingId;
import com.cotani.market.api.MarketListingStateException;
import com.cotani.market.api.MarketListingStatus;
import com.cotani.market.api.MarketNotFoundException;
import com.cotani.market.api.MarketPage;
import com.cotani.market.api.MarketPurchase;
import com.cotani.market.api.MarketPurchaseId;
import com.cotani.market.api.MarketPurchaseRequest;
import com.cotani.market.api.MarketPurchaseReservation;
import com.cotani.market.api.MarketPurchaseStatus;
import com.cotani.market.api.MarketQuery;
import com.cotani.market.api.MarketRepository;
import com.cotani.market.api.MarketSelfPurchaseException;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.ParameterBinder;
import com.cotani.storage.query.Row;
import com.cotani.storage.transaction.TransactionContext;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/** SQL-backed repository with transactional reservations and idempotent settlement receipts. */
public final class StorageMarketRepository implements MarketRepository {
    private static final String LISTINGS = "cotani_market_listings";
    private static final String PURCHASES = "cotani_market_purchases";

    private final CotaniStorage storage;

    public StorageMarketRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<MarketListing> createAsync(MarketListing listing) {
        Objects.requireNonNull(listing, "listing");
        return insertListing(listing).handle((ignored, failure) -> failure).thenCompose(failure -> {
            if (failure == null) {
                return completed(listing);
            }
            var cause = unwrap(failure);
            if (!isUniqueViolation(cause)) {
                return failed(cause);
            }
            return findAsync(listing.id()).thenCompose(existing -> {
                if (existing.isEmpty()) {
                    return failed(cause);
                }
                return existing.orElseThrow().equals(listing)
                        ? completed(existing.orElseThrow())
                        : failed(new MarketConflictException(listing.id()));
            });
        });
    }

    @Override
    public CompletionStage<Optional<MarketListing>> findAsync(MarketListingId listingId) {
        Objects.requireNonNull(listingId, "listingId");
        return storage.queryExecutor()
                .queryOne(
                        "SELECT listing_id, seller_id, item_key, item_amount, item_data, currency_id, price, created_at, "
                                + "expires_at, status FROM " + LISTINGS + " WHERE listing_id = ?",
                        binder -> binder.uuid(listingId.value()),
                        StorageMarketRepository::mapListing);
    }

    @Override
    public CompletionStage<MarketPage> browseAsync(MarketQuery query, Instant now) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(now, "now");
        if (query.pageSize() == Integer.MAX_VALUE) {
            return failed(new IllegalArgumentException("pageSize is too large"));
        }
        var sql =
                new StringBuilder("SELECT listing_id, seller_id, item_key, item_amount, item_data, currency_id, price, "
                        + "created_at, expires_at, status FROM " + LISTINGS
                        + " WHERE status = ? AND expires_at > ?");
        var parameters = new ArrayList<Object>();
        parameters.add(MarketListingStatus.ACTIVE.name());
        parameters.add(now);
        if (query.itemKey().isPresent()) {
            sql.append(" AND item_key = ?");
            parameters.add(query.itemKey().get());
        }
        if (query.currency().isPresent()) {
            sql.append(" AND currency_id = ?");
            parameters.add(query.currency().get().value());
        }
        if (query.sellerId().isPresent()) {
            sql.append(" AND seller_id = ?");
            parameters.add(query.sellerId().get());
        }
        sql.append(" ORDER BY created_at DESC, listing_id DESC LIMIT ? OFFSET ?");
        parameters.add(query.pageSize() + 1);
        parameters.add((long) query.page() * query.pageSize());
        return storage.queryExecutor()
                .queryMany(
                        sql.toString(), binder -> bindObjects(binder, parameters), StorageMarketRepository::mapListing)
                .thenApply(rows -> {
                    var hasMore = rows.size() > query.pageSize();
                    return new MarketPage(
                            new ArrayList<>(rows.subList(0, Math.min(rows.size(), query.pageSize()))), hasMore);
                });
    }

    @Override
    public CompletionStage<MarketListing> cancelAsync(UUID sellerId, MarketListingId listingId, Instant now) {
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(listingId, "listingId");
        Objects.requireNonNull(now, "now");
        return storage.transactions()
                .runAsync(transaction -> cancelInTransaction(transaction, sellerId, listingId, now))
                .thenCompose(listing -> listing.status() == MarketListingStatus.EXPIRED
                        ? failed(new MarketListingStateException(listing.id(), MarketListingStatus.EXPIRED))
                        : completed(listing));
    }

    @Override
    public CompletionStage<MarketPurchaseReservation> reservePurchaseAsync(MarketPurchaseRequest request, Instant now) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        return storage.transactions()
                .runAsync(transaction -> reserveInTransaction(transaction, request, now))
                .handle((outcome, failure) -> new ReservationResult(outcome, failure))
                .thenCompose(result -> {
                    if (result.failure() == null) {
                        var outcome = result.outcome();
                        if (outcome.expiredListing().isPresent()) {
                            var expired = outcome.expiredListing().orElseThrow();
                            return failed(new MarketListingStateException(expired.id(), expired.status()));
                        }
                        return completed(
                                new MarketPurchaseReservation(outcome.purchase().orElseThrow(), outcome.created()));
                    }
                    var cause = unwrap(result.failure());
                    if (!isUniqueViolation(cause)) {
                        return failed(cause);
                    }
                    return findPurchaseAsync(request.purchaseId()).thenCompose(existing -> {
                        if (existing.isEmpty()) {
                            return failed(cause);
                        }
                        var purchase = existing.orElseThrow();
                        return purchase.listingId().equals(request.listingId())
                                        && purchase.buyerId().equals(request.buyerId())
                                ? completed(new MarketPurchaseReservation(purchase, false))
                                : failed(new MarketConflictException(request.purchaseId()));
                    });
                });
    }

    @Override
    public CompletionStage<Optional<MarketPurchase>> findPurchaseAsync(MarketPurchaseId purchaseId) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        return storage.queryExecutor()
                .queryOne(
                        purchaseSelectSql(),
                        binder -> binder.uuid(purchaseId.value()),
                        StorageMarketRepository::mapPurchase);
    }

    @Override
    public CompletionStage<Boolean> markSettledAsync(MarketPurchaseId purchaseId, Instant settledAt) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(settledAt, "settledAt");
        return storage.transactions()
                .runAsync(transaction -> markSettledInTransaction(transaction, purchaseId, settledAt));
    }

    @Override
    public CompletionStage<Boolean> releasePendingAsync(MarketPurchaseId purchaseId, Instant now) {
        Objects.requireNonNull(purchaseId, "purchaseId");
        Objects.requireNonNull(now, "now");
        return storage.transactions()
                .runAsync(transaction -> releasePendingInTransaction(transaction, purchaseId, now));
    }

    @Override
    public CompletionStage<List<MarketPurchase>> pendingPurchasesAsync(int limit) {
        validateLimit(limit);
        return storage.queryExecutor()
                .queryMany(
                        "SELECT purchase_id, listing_id, seller_id, buyer_id, item_key, item_amount, item_data, currency_id, "
                                + "price, reserved_at, status, settled_at FROM " + PURCHASES
                                + " WHERE status = ? ORDER BY reserved_at ASC, purchase_id ASC LIMIT ?",
                        binder -> {
                            binder.string(MarketPurchaseStatus.PENDING.name());
                            binder.integer(limit);
                        },
                        StorageMarketRepository::mapPurchase);
    }

    @Override
    public CompletionStage<Void> purgeAsync(Instant before) {
        Objects.requireNonNull(before, "before");
        return storage.transactions()
                .runAsync(transaction -> transaction
                        .update("DELETE FROM " + PURCHASES + " WHERE status IN (?, ?) AND reserved_at < ?", binder -> {
                            binder.string(MarketPurchaseStatus.SETTLED.name());
                            binder.string(MarketPurchaseStatus.CANCELLED.name());
                            binder.instant(before);
                        })
                        .thenCompose(ignored -> transaction.update(
                                "DELETE FROM " + LISTINGS + " WHERE status IN (?, ?, ?) AND expires_at < ?", binder -> {
                                    binder.string(MarketListingStatus.SOLD.name());
                                    binder.string(MarketListingStatus.CANCELLED.name());
                                    binder.string(MarketListingStatus.EXPIRED.name());
                                    binder.instant(before);
                                })));
    }

    public static List<Migration> migrations() {
        return List.of(new CreateMarketTablesMigration(), new CreateMarketIndexesMigration());
    }

    private CompletionStage<Void> insertListing(MarketListing listing) {
        return storage.queryExecutor()
                .update(
                        "INSERT INTO " + LISTINGS
                                + " (listing_id, seller_id, item_key, item_amount, item_data, currency_id, price, created_at, expires_at, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        binder -> {
                            binder.uuid(listing.id().value());
                            binder.uuid(listing.sellerId());
                            binder.string(listing.item().key());
                            binder.integer(listing.item().amount());
                            binder.string(listing.item().serializedData());
                            binder.string(listing.price().currency().value());
                            binder.string(listing.price().amount().toPlainString());
                            binder.instant(listing.createdAt());
                            binder.instant(listing.expiresAt());
                            binder.string(listing.status().name());
                        });
    }

    private CompletionStage<MarketListing> cancelInTransaction(
            TransactionContext transaction, UUID sellerId, MarketListingId listingId, Instant now) {
        return transaction
                .queryOne(
                        listingSelectSql(true),
                        binder -> binder.uuid(listingId.value()),
                        StorageMarketRepository::mapListing)
                .thenCompose(existing -> {
                    var current = existing.orElseThrow(() -> new MarketNotFoundException("listing", listingId.value()));
                    if (!current.sellerId().equals(sellerId)) {
                        return failed(new MarketListingStateException(listingId, current.status()));
                    }
                    if (current.status() == MarketListingStatus.ACTIVE
                            && !current.expiresAt().isAfter(now)) {
                        return updateListingStatus(transaction, listingId, MarketListingStatus.EXPIRED)
                                .thenApply(ignored -> current.withStatus(MarketListingStatus.EXPIRED));
                    }
                    if (!current.isActiveAt(now)) {
                        return failed(new MarketListingStateException(listingId, current.status()));
                    }
                    var cancelled = current.withStatus(MarketListingStatus.CANCELLED);
                    return updateListingStatus(transaction, listingId, MarketListingStatus.CANCELLED)
                            .thenApply(ignored -> cancelled);
                });
    }

    private CompletionStage<ReservationOutcome> reserveInTransaction(
            TransactionContext transaction, MarketPurchaseRequest request, Instant now) {
        return transaction
                .queryOne(
                        purchaseSelectSql(),
                        binder -> binder.uuid(request.purchaseId().value()),
                        StorageMarketRepository::mapPurchase)
                .thenCompose(existing -> {
                    if (existing.isPresent()) {
                        var purchase = existing.orElseThrow();
                        if (!purchase.listingId().equals(request.listingId())
                                || !purchase.buyerId().equals(request.buyerId())) {
                            return failed(new MarketConflictException(request.purchaseId()));
                        }
                        return completed(ReservationOutcome.existing(purchase));
                    }
                    return transaction
                            .queryOne(
                                    listingSelectSql(true),
                                    binder -> binder.uuid(request.listingId().value()),
                                    StorageMarketRepository::mapListing)
                            .thenCompose(listing -> {
                                var current = listing.orElseThrow(() -> new MarketNotFoundException(
                                        "listing", request.listingId().value()));
                                if (current.sellerId().equals(request.buyerId())) {
                                    return failed(new MarketSelfPurchaseException(current.id()));
                                }
                                if (!current.isActiveAt(now)) {
                                    if (current.status() == MarketListingStatus.ACTIVE) {
                                        return updateListingStatus(
                                                        transaction, current.id(), MarketListingStatus.EXPIRED)
                                                .thenApply(ignored -> ReservationOutcome.expired(
                                                        current.withStatus(MarketListingStatus.EXPIRED)));
                                    }
                                    return failed(new MarketListingStateException(current.id(), current.status()));
                                }
                                var pending = MarketPurchase.pending(request, current, now);
                                return insertPurchase(transaction, pending)
                                        .thenCompose(ignored -> updateListingStatus(
                                                transaction, current.id(), MarketListingStatus.PURCHASE_PENDING))
                                        .thenApply(ignored -> ReservationOutcome.created(pending));
                            });
                });
    }

    private CompletionStage<Boolean> markSettledInTransaction(
            TransactionContext transaction, MarketPurchaseId purchaseId, Instant settledAt) {
        return transaction
                .queryOne(
                        purchaseSelectSql(true),
                        binder -> binder.uuid(purchaseId.value()),
                        StorageMarketRepository::mapPurchase)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        return completed(false);
                    }
                    var purchase = existing.orElseThrow();
                    if (purchase.status() == MarketPurchaseStatus.SETTLED) {
                        return completed(true);
                    }
                    if (purchase.status() == MarketPurchaseStatus.CANCELLED) {
                        return completed(false);
                    }
                    return transaction
                            .queryOne(
                                    listingSelectSql(true),
                                    binder -> binder.uuid(purchase.listingId().value()),
                                    StorageMarketRepository::mapListing)
                            .thenCompose(listing -> {
                                if (listing.isEmpty()
                                        || listing.orElseThrow().status() != MarketListingStatus.PURCHASE_PENDING) {
                                    return completed(false);
                                }
                                return transaction
                                        .update(
                                                "UPDATE " + PURCHASES
                                                        + " SET status = ?, settled_at = ? WHERE purchase_id = ?",
                                                binder -> {
                                                    binder.string(MarketPurchaseStatus.SETTLED.name());
                                                    binder.instant(settledAt);
                                                    binder.uuid(purchaseId.value());
                                                })
                                        .thenCompose(ignored -> updateListingStatus(
                                                transaction, purchase.listingId(), MarketListingStatus.SOLD))
                                        .thenApply(ignored -> true);
                            });
                });
    }

    private CompletionStage<Boolean> releasePendingInTransaction(
            TransactionContext transaction, MarketPurchaseId purchaseId, Instant now) {
        return transaction
                .queryOne(
                        purchaseSelectSql(true),
                        binder -> binder.uuid(purchaseId.value()),
                        StorageMarketRepository::mapPurchase)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        return completed(false);
                    }
                    var purchase = existing.orElseThrow();
                    if (purchase.status() == MarketPurchaseStatus.CANCELLED) {
                        return completed(true);
                    }
                    if (purchase.status() != MarketPurchaseStatus.PENDING) {
                        return completed(false);
                    }
                    return transaction
                            .queryOne(
                                    listingSelectSql(true),
                                    binder -> binder.uuid(purchase.listingId().value()),
                                    StorageMarketRepository::mapListing)
                            .thenCompose(listing -> {
                                var current = listing.orElseThrow(() -> new MarketNotFoundException(
                                        "listing", purchase.listingId().value()));
                                var nextListingStatus = current.expiresAt().isAfter(now)
                                        ? MarketListingStatus.ACTIVE
                                        : MarketListingStatus.EXPIRED;
                                return transaction
                                        .update(
                                                "UPDATE " + PURCHASES + " SET status = ? WHERE purchase_id = ?",
                                                binder -> {
                                                    binder.string(MarketPurchaseStatus.CANCELLED.name());
                                                    binder.uuid(purchaseId.value());
                                                })
                                        .thenCompose(ignored -> updateListingStatus(
                                                transaction, purchase.listingId(), nextListingStatus))
                                        .thenApply(ignored -> true);
                            });
                });
    }

    private CompletionStage<Void> insertPurchase(TransactionContext transaction, MarketPurchase purchase) {
        return transaction.update(
                "INSERT INTO " + PURCHASES
                        + " (purchase_id, listing_id, seller_id, buyer_id, item_key, item_amount, item_data, currency_id, price, reserved_at, status, settled_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                binder -> {
                    binder.uuid(purchase.id().value());
                    binder.uuid(purchase.listingId().value());
                    binder.uuid(purchase.sellerId());
                    binder.uuid(purchase.buyerId());
                    binder.string(purchase.item().key());
                    binder.integer(purchase.item().amount());
                    binder.string(purchase.item().serializedData());
                    binder.string(purchase.price().currency().value());
                    binder.string(purchase.price().amount().toPlainString());
                    binder.instant(purchase.reservedAt());
                    binder.string(purchase.status().name());
                    binder.set(null);
                });
    }

    private CompletionStage<Void> updateListingStatus(
            TransactionContext transaction, MarketListingId listingId, MarketListingStatus status) {
        return transaction.update("UPDATE " + LISTINGS + " SET status = ? WHERE listing_id = ?", binder -> {
            binder.string(status.name());
            binder.uuid(listingId.value());
        });
    }

    private String listingSelectSql(boolean forUpdate) {
        return "SELECT listing_id, seller_id, item_key, item_amount, item_data, currency_id, price, created_at, expires_at, status FROM "
                + LISTINGS + " WHERE listing_id = ?" + lockSuffix(forUpdate);
    }

    private String purchaseSelectSql() {
        return purchaseSelectSql(false);
    }

    private String purchaseSelectSql(boolean forUpdate) {
        return "SELECT purchase_id, listing_id, seller_id, buyer_id, item_key, item_amount, item_data, currency_id, price, reserved_at, status, settled_at FROM "
                + PURCHASES + " WHERE purchase_id = ?" + lockSuffix(forUpdate);
    }

    private String lockSuffix(boolean forUpdate) {
        return forUpdate && !storage.dialect().name().equals("sqlite") ? " FOR UPDATE" : "";
    }

    private static MarketListing mapListing(Row row) throws SQLException {
        return new MarketListing(
                new MarketListingId(UUID.fromString(row.getString("listing_id"))),
                row.getUuidOptional("seller_id").orElseThrow(),
                new com.cotani.market.api.MarketItem(
                        row.getString("item_key"), row.getInt("item_amount"), row.getString("item_data")),
                new com.cotani.market.api.MarketPrice(
                        CurrencyId.of(row.getString("currency_id")), new BigDecimal(row.getString("price"))),
                row.getInstantOptional("created_at").orElseThrow(),
                row.getInstantOptional("expires_at").orElseThrow(),
                MarketListingStatus.valueOf(row.getString("status")));
    }

    private static MarketPurchase mapPurchase(Row row) throws SQLException {
        var status = MarketPurchaseStatus.valueOf(row.getString("status"));
        return new MarketPurchase(
                new MarketPurchaseId(UUID.fromString(row.getString("purchase_id"))),
                new MarketListingId(UUID.fromString(row.getString("listing_id"))),
                row.getUuidOptional("seller_id").orElseThrow(),
                row.getUuidOptional("buyer_id").orElseThrow(),
                new com.cotani.market.api.MarketItem(
                        row.getString("item_key"), row.getInt("item_amount"), row.getString("item_data")),
                new com.cotani.market.api.MarketPrice(
                        CurrencyId.of(row.getString("currency_id")), new BigDecimal(row.getString("price"))),
                row.getInstantOptional("reserved_at").orElseThrow(),
                status,
                row.getInstantOptional("settled_at"));
    }

    private static void bindObjects(ParameterBinder binder, List<Object> values) throws SQLException {
        for (var value : values) {
            binder.set(value);
        }
    }

    private static void validateLimit(int limit) {
        if (limit <= 0 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private static boolean isUniqueViolation(Throwable failure) {
        var current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                var state = sqlException.getSQLState();
                if (state != null && state.startsWith("23")) {
                    return true;
                }
            }
            var message = current.getMessage();
            if (message != null) {
                var normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("unique constraint")
                        || normalized.contains("duplicate entry")
                        || normalized.contains("primary key")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
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

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private record ReservationResult(ReservationOutcome outcome, Throwable failure) {}

    private record ReservationOutcome(
            Optional<MarketPurchase> purchase, Optional<MarketListing> expiredListing, boolean created) {
        private ReservationOutcome {
            Objects.requireNonNull(purchase, "purchase");
            Objects.requireNonNull(expiredListing, "expiredListing");
            if (purchase.isPresent() == expiredListing.isPresent()) {
                throw new IllegalArgumentException("reservation outcome must contain exactly one result");
            }
        }

        private static ReservationOutcome existing(MarketPurchase purchase) {
            return new ReservationOutcome(Optional.of(purchase), Optional.empty(), false);
        }

        private static ReservationOutcome created(MarketPurchase purchase) {
            return new ReservationOutcome(Optional.of(purchase), Optional.empty(), true);
        }

        private static ReservationOutcome expired(MarketListing listing) {
            return new ReservationOutcome(Optional.empty(), Optional.of(listing), false);
        }
    }
}
