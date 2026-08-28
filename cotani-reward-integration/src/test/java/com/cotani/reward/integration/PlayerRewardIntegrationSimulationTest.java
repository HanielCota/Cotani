package com.cotani.reward.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.economy.EconomyService;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.transaction.EconomyBalanceChange;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionDetails;
import com.cotani.reward.api.CurrencyGrant;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardGrantHandler.RewardSettlementContext;
import com.cotani.reward.api.RewardId;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/** Simulates many independent players claiming deterministic currency rewards. */
@Tag("stress")
@Tag("player-simulation")
class PlayerRewardIntegrationSimulationTest {
    private static final int SCENARIO_COUNT = 1_201;

    @TestFactory
    Stream<DynamicTest> playerClaimsUseStableIdempotencyKeys() {
        var economyService = mock(EconomyService.class);
        when(economyService.deposit(
                        any(), any(CurrencyId.class), any(BigDecimal.class), any(), any(EconomyOperationId.class)))
                .thenReturn(CompletableFuture.completedFuture(transaction()));
        var handler = new RewardEconomyGrantHandler(economyService);

        return IntStream.range(0, SCENARIO_COUNT)
                .mapToObj(index -> DynamicTest.dynamicTest("reward claim " + index, () -> {
                    var playerId = namedUuid("reward-player:" + index);
                    var claimId = new RewardClaimId(namedUuid("reward-claim:" + index));
                    var grantIndex = index % 16;
                    var context = new RewardSettlementContext(playerId, claimId, new RewardId("daily"), grantIndex);
                    var amount = BigDecimal.valueOf(1L + index % 25L);
                    var normalizedAmount = amount.stripTrailingZeros();
                    var expectedOperation =
                            EconomyOperationId.of(namedUuid(claimId.value() + ":currency:" + grantIndex));

                    handler.settleAsync(context, new CurrencyGrant(" coins ", amount))
                            .toCompletableFuture()
                            .join();

                    verify(economyService)
                            .deposit(
                                    eq(playerId),
                                    eq(CurrencyId.of("coins")),
                                    eq(normalizedAmount),
                                    eq(EconomyReason.system("reward.claim")),
                                    eq(expectedOperation));
                }));
    }

    private static EconomyTransaction transaction() {
        var details = new EconomyTransactionDetails(
                EconomyOperationId.of(namedUuid("transaction")),
                CurrencyId.of("coins"),
                BigDecimal.ONE,
                EconomyReason.system("test"),
                Instant.EPOCH);
        var change = new EconomyBalanceChange(namedUuid("transaction-player"), BigDecimal.ZERO, BigDecimal.ONE);
        return EconomyTransaction.deposit(details, change);
    }

    private static UUID namedUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
