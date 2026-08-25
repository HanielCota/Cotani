package com.cotani.market;

import com.cotani.event.api.EventBus;
import com.cotani.market.api.MarketRepository;
import com.cotani.market.api.MarketService;
import com.cotani.market.api.MarketServiceOptions;
import com.cotani.market.api.MarketSettlementService;
import com.cotani.market.internal.DefaultMarketServiceFactory;
import com.cotani.market.internal.InMemoryMarketRepository;
import java.time.Clock;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Factories for the {@code cotani-market} module. */
public final class CotaniMarkets {
    private CotaniMarkets() {}

    /** Creates an ephemeral in-memory marketplace, suitable for tests. */
    public static MarketService inMemory(MarketSettlementService settlementService) {
        return fromRepository(new InMemoryMarketRepository(), settlementService, null, MarketServiceOptions.defaults());
    }

    /** Creates a service over a caller-owned repository and settlement adapter. */
    public static MarketService fromRepository(MarketRepository repository, MarketSettlementService settlementService) {
        return fromRepository(repository, settlementService, null, MarketServiceOptions.defaults());
    }

    /** Creates a service with optional event publication and explicit operational limits. */
    public static MarketService fromRepository(
            MarketRepository repository,
            MarketSettlementService settlementService,
            @Nullable EventBus eventBus,
            MarketServiceOptions options) {
        return DefaultMarketServiceFactory.create(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(settlementService, "settlementService"),
                eventBus,
                Objects.requireNonNull(options, "options"),
                Clock.systemUTC());
    }
}
