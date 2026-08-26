package com.cotani.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.market.api.MarketItem;
import com.cotani.market.api.MarketListing;
import com.cotani.market.api.MarketListingRequest;
import com.cotani.market.api.MarketListingStatus;
import com.cotani.market.api.MarketPage;
import com.cotani.market.api.MarketPrice;
import com.cotani.market.api.MarketPurchasePendingException;
import com.cotani.market.api.MarketPurchaseRequest;
import com.cotani.market.api.MarketPurchaseStatus;
import com.cotani.market.api.MarketQuery;
import com.cotani.market.api.MarketService;
import com.cotani.market.api.MarketSettlementService;
import com.cotani.market.api.MarketSettlementStatus;
import com.cotani.market.internal.InMemoryMarketRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MarketServiceTest {
    private @Nullable MarketService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void listsBrowsesAndCancelsOwnListing() {
        var repository = new InMemoryMarketRepository();
        service = CotaniMarkets.fromRepository(repository, purchase -> CompletableFuture.completedFuture(null));
        var seller = UUID.randomUUID();
        var now = Instant.now();
        var request = listingRequest(seller, now);

        var created = service.listAsync(request).toCompletableFuture().join();
        MarketPage page = service.browseAsync(MarketQuery.firstPage(10))
                .toCompletableFuture()
                .join();

        assertEquals(created, page.listings().getFirst());
        assertTrue(page.listings().getFirst().isActiveAt(now.plus(Duration.ofHours(1))));

        var cancelled =
                service.cancelAsync(seller, created.id()).toCompletableFuture().join();

        assertEquals(MarketListingStatus.CANCELLED, cancelled.status());
        assertTrue(service.browseAsync(MarketQuery.firstPage(10))
                .toCompletableFuture()
                .join()
                .listings()
                .isEmpty());
    }

    @Test
    void purchaseIsIdempotentAndMarksListingSold() {
        var repository = new InMemoryMarketRepository();
        var settlement = new CountingSettlement();
        service = CotaniMarkets.fromRepository(repository, settlement);
        var listing = service.listAsync(listingRequest(UUID.randomUUID(), Instant.now()))
                .toCompletableFuture()
                .join();
        var request = MarketPurchaseRequest.create(listing.id(), UUID.randomUUID());

        var first = service.purchaseAsync(request).toCompletableFuture().join();
        var second = service.purchaseAsync(request).toCompletableFuture().join();
        var storedListing =
                service.findAsync(listing.id()).toCompletableFuture().join().orElseThrow();

        assertEquals(MarketPurchaseStatus.SETTLED, first.status());
        assertEquals(first, second);
        assertEquals(1, settlement.calls());
        assertEquals(MarketListingStatus.SOLD, storedListing.status());
    }

    @Test
    void failedSettlementLeavesPurchaseRecoverableWithSameId() {
        var repository = new InMemoryMarketRepository();
        var settlement = new RecoveringSettlement();
        var currentService = CotaniMarkets.fromRepository(repository, settlement);
        service = currentService;
        var listing = currentService
                .listAsync(listingRequest(UUID.randomUUID(), Instant.now()))
                .toCompletableFuture()
                .join();
        var request = MarketPurchaseRequest.create(listing.id(), UUID.randomUUID());

        var firstFailure = assertThrows(
                CompletionException.class,
                () -> currentService
                        .purchaseAsync(request)
                        .toCompletableFuture()
                        .join());
        assertTrue(firstFailure.getCause() instanceof MarketPurchasePendingException);
        assertEquals(
                MarketPurchaseStatus.PENDING,
                currentService
                        .findPurchaseAsync(request.purchaseId())
                        .toCompletableFuture()
                        .join()
                        .orElseThrow()
                        .status());

        settlement.allowSettlement();
        var settled =
                currentService.purchaseAsync(request).toCompletableFuture().join();

        assertEquals(MarketPurchaseStatus.SETTLED, settled.status());
        assertEquals(2, settlement.calls());
    }

    @Test
    void pendingSettlementDoesNotBlockIndependentOperationsOrClose() {
        var settlement = new HangingSettlement();
        var currentService = CotaniMarkets.fromRepository(
                new InMemoryMarketRepository(),
                settlement,
                null,
                new com.cotani.market.api.MarketServiceOptions(
                        Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(25), 50, 100));
        service = currentService;
        var listing = currentService
                .listAsync(listingRequest(UUID.randomUUID(), Instant.now()))
                .toCompletableFuture()
                .join();
        var request = MarketPurchaseRequest.create(listing.id(), UUID.randomUUID());

        assertThrows(
                CompletionException.class,
                () -> currentService
                        .purchaseAsync(request)
                        .toCompletableFuture()
                        .join());

        var secondListing = currentService
                .listAsync(listingRequest(UUID.randomUUID(), Instant.now()))
                .toCompletableFuture()
                .join();

        assertEquals(secondListing.status(), MarketListingStatus.ACTIVE);
        currentService.closeAsync().toCompletableFuture().join();
    }

    @Test
    void failedSettlementCanBeSafelyReleased() {
        var repository = new InMemoryMarketRepository();
        var settlement = new FailedSettlement();
        var currentService = CotaniMarkets.fromRepository(repository, settlement);
        service = currentService;
        var listing = currentService
                .listAsync(listingRequest(UUID.randomUUID(), Instant.now()))
                .toCompletableFuture()
                .join();
        var request = MarketPurchaseRequest.create(listing.id(), UUID.randomUUID());

        assertThrows(
                CompletionException.class,
                () -> currentService
                        .purchaseAsync(request)
                        .toCompletableFuture()
                        .join());

        var released = currentService
                .releasePendingAsync(request.purchaseId())
                .toCompletableFuture()
                .join();

        assertEquals(MarketPurchaseStatus.CANCELLED, released.status());
        assertEquals(
                MarketListingStatus.ACTIVE,
                currentService
                        .findAsync(listing.id())
                        .toCompletableFuture()
                        .join()
                        .orElseThrow()
                        .status());
    }

    @Test
    void unknownSettlementStatusKeepsReservationPending() {
        var repository = new InMemoryMarketRepository();
        var currentService = CotaniMarkets.fromRepository(repository, new RecoveringSettlement());
        service = currentService;
        var listing = currentService
                .listAsync(listingRequest(UUID.randomUUID(), Instant.now()))
                .toCompletableFuture()
                .join();
        var request = MarketPurchaseRequest.create(listing.id(), UUID.randomUUID());

        assertThrows(
                CompletionException.class,
                () -> currentService
                        .purchaseAsync(request)
                        .toCompletableFuture()
                        .join());

        var failure = assertThrows(
                CompletionException.class,
                () -> currentService
                        .releasePendingAsync(request.purchaseId())
                        .toCompletableFuture()
                        .join());

        assertTrue(failure.getCause() instanceof MarketPurchasePendingException);
        assertEquals(
                MarketListingStatus.PURCHASE_PENDING,
                currentService
                        .findAsync(listing.id())
                        .toCompletableFuture()
                        .join()
                        .orElseThrow()
                        .status());
    }

    @Test
    void expiredCancellationIsPersistedBeforeFailure() {
        var repository = new InMemoryMarketRepository();
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        var listing = MarketListing.active(listingRequest(UUID.randomUUID(), createdAt));
        repository.createAsync(listing).toCompletableFuture().join();

        assertThrows(
                CompletionException.class,
                () -> repository
                        .cancelAsync(listing.sellerId(), listing.id(), createdAt.plus(Duration.ofHours(3)))
                        .toCompletableFuture()
                        .join());

        assertEquals(
                MarketListingStatus.EXPIRED,
                repository
                        .findAsync(listing.id())
                        .toCompletableFuture()
                        .join()
                        .orElseThrow()
                        .status());
    }

    @Test
    void queryRejectsInvalidItemKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketQuery.builder()
                        .pageSize(10)
                        .itemKey("minecraft:diamond with spaces")
                        .build());
    }

    private static MarketListingRequest listingRequest(UUID sellerId, Instant createdAt) {
        return MarketListingRequest.create(
                sellerId,
                new MarketItem("minecraft:diamond", 3, "serialized-item"),
                new MarketPrice(CurrencyId.of("coins"), new BigDecimal("12.50")),
                Duration.ofHours(2),
                createdAt);
    }

    private static final class CountingSettlement implements MarketSettlementService {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletionStage<Void> settleAsync(com.cotani.market.api.MarketPurchase purchase) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class RecoveringSettlement implements MarketSettlementService {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile boolean allowed;

        @Override
        public CompletionStage<Void> settleAsync(com.cotani.market.api.MarketPurchase purchase) {
            calls.incrementAndGet();
            return allowed
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(new IllegalStateException("temporary settlement failure"));
        }

        void allowSettlement() {
            allowed = true;
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class HangingSettlement implements MarketSettlementService {
        @Override
        public CompletionStage<Void> settleAsync(com.cotani.market.api.MarketPurchase purchase) {
            return new CompletableFuture<>();
        }
    }

    private static final class FailedSettlement implements MarketSettlementService {
        @Override
        public CompletionStage<Void> settleAsync(com.cotani.market.api.MarketPurchase purchase) {
            return CompletableFuture.failedFuture(new IllegalStateException("permanent settlement failure"));
        }

        @Override
        public CompletionStage<MarketSettlementStatus> statusAsync(com.cotani.market.api.MarketPurchase purchase) {
            return CompletableFuture.completedFuture(MarketSettlementStatus.FAILED);
        }
    }
}
