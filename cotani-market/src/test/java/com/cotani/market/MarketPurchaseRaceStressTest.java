package com.cotani.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.market.api.MarketItem;
import com.cotani.market.api.MarketListingRequest;
import com.cotani.market.api.MarketListingStatus;
import com.cotani.market.api.MarketPrice;
import com.cotani.market.api.MarketPurchaseId;
import com.cotani.market.api.MarketPurchaseRequest;
import com.cotani.market.internal.InMemoryMarketRepository;
import com.cotani.testkit.StressTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class MarketPurchaseRaceStressTest {
    @Test
    void oneListingCanBeSettledByOnlyOneOfOneThousandConcurrentBuyers() {
        var settlements = new AtomicInteger();
        var service = CotaniMarkets.fromRepository(new InMemoryMarketRepository(), purchase -> {
            settlements.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        try {
            var listing = service.listAsync(MarketListingRequest.create(
                            new UUID(1L, 1L),
                            new MarketItem("minecraft:diamond", 1, "diamond"),
                            new MarketPrice(CurrencyId.of("coins"), new BigDecimal("100")),
                            Duration.ofHours(1),
                            Instant.parse("2030-01-01T00:00:00Z")))
                    .toCompletableFuture()
                    .join();

            var outcomes = StressTestSupport.concurrent(
                    "market",
                    "single-listing-race",
                    1_000,
                    32,
                    Duration.ofSeconds(30),
                    index -> service.purchaseAsync(new MarketPurchaseRequest(
                                    new MarketPurchaseId(new UUID(2L, index + 1L)),
                                    listing.id(),
                                    new UUID(3L, index + 1L)))
                            .handle((purchase, failure) -> failure == null));

            assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, settlements.get());
            assertEquals(
                    MarketListingStatus.SOLD,
                    service.findAsync(listing.id())
                            .toCompletableFuture()
                            .join()
                            .orElseThrow()
                            .status());
        } finally {
            service.close();
        }
    }
}
