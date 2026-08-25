package com.cotani.market.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.market.api.MarketRepository;
import com.cotani.market.api.MarketService;
import com.cotani.market.api.MarketServiceOptions;
import com.cotani.market.api.MarketSettlementService;
import java.time.Clock;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal factory kept separate from the public bootstrap facade. */
@InternalApi
public final class DefaultMarketServiceFactory {
    private DefaultMarketServiceFactory() {}

    public static MarketService create(
            MarketRepository repository,
            MarketSettlementService settlementService,
            @Nullable EventBus eventBus,
            MarketServiceOptions options,
            Clock clock) {
        return new DefaultMarketService(
                Objects.requireNonNull(repository, "repository"),
                Objects.requireNonNull(settlementService, "settlementService"),
                eventBus,
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }
}
