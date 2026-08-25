package com.cotani.reward.integration;

import com.cotani.economy.EconomyService;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.RewardGrant;
import com.cotani.reward.api.RewardGrantHandler;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Settles {@link CurrencyGrant}s through the idempotent Cotani economy API. */
public final class RewardEconomyGrantHandler implements RewardGrantHandler {
    private final EconomyService economyService;

    public RewardEconomyGrantHandler(EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    @Override
    public boolean supports(RewardGrant grant) {
        return grant instanceof CurrencyGrant;
    }

    @Override
    public CompletionStage<Void> settleAsync(RewardSettlementContext context, RewardGrant grant) {
        if (!(grant instanceof CurrencyGrant currency)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Unsupported grant: " + grant.getClass().getName()));
        }
        try {
            var operationId = EconomyOperationId.of(deterministicOperationId(context));
            return economyService
                    .deposit(
                            context.playerId(),
                            CurrencyId.of(currency.currency()),
                            currency.amount(),
                            EconomyReason.system("reward.claim"),
                            operationId)
                    .thenApply(ignored -> null);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static UUID deterministicOperationId(RewardSettlementContext context) {
        var value = context.claimId().value() + ":currency:" + context.grantIndex();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
