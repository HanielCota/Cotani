package com.cotani.economy.internal.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cotani.economy.EconomySettings;
import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.currency.EconomyCurrency;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Deterministic player journeys covering a large set of economy inputs. */
@Tag("stress")
class PlayerEconomySimulationTest {
    private static final int SCENARIO_COUNT = 1_201;
    private static final EconomySettings SETTINGS = EconomySettings.defaultSettings(EconomyCurrency.coins());
    private static final CurrencyId CURRENCY = SETTINGS.defaultCurrency().id();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @TestFactory
    Stream<DynamicTest> playerJourneysAreAtomicAndIdempotent() {
        return IntStream.range(0, SCENARIO_COUNT)
                .mapToObj(index -> DynamicTest.dynamicTest("player journey " + index, () -> runJourney(index)));
    }

    @Test
    void manyPlayerActionsPreserveEachPlayerBalance() {
        var store = new InMemoryEconomyStore(DIRECT_EXECUTOR, CLOCK, SETTINGS);

        var actions = IntStream.range(0, SCENARIO_COUNT)
                .mapToObj(index -> store.deposit(
                        playerId(index),
                        CURRENCY,
                        amountFor(index),
                        playerReason("player.reward", index),
                        operationId("concurrent-deposit", index)))
                .toArray();

        assertEquals(SCENARIO_COUNT, actions.length);

        for (int index = 0; index < SCENARIO_COUNT; index++) {
            var account = store.getOrCreate(playerId(index), CURRENCY).join();
            assertEquals(
                    SETTINGS.startingBalance().add(amountFor(index)),
                    account.balance(),
                    "balance mismatch for player " + index);
        }
    }

    private static void runJourney(int index) {
        var store = new InMemoryEconomyStore(DIRECT_EXECUTOR, CLOCK, SETTINGS);
        var playerId = playerId(index);
        var recipientId = recipientId(index);
        var amount = amountFor(index);

        store.deposit(playerId, CURRENCY, amount, playerReason("player.reward", index), operationId("deposit", index))
                .join();
        store.withdraw(
                        playerId,
                        CURRENCY,
                        BigDecimal.TEN,
                        playerReason("player.purchase", index),
                        operationId("withdraw", index))
                .join();
        var transfer = store.transfer(
                        playerId,
                        recipientId,
                        CURRENCY,
                        BigDecimal.valueOf(5),
                        playerReason("player.pay", index),
                        operationId("transfer", index))
                .join();

        var repeatedDeposit = store.deposit(
                        playerId, CURRENCY, amount, playerReason("player.reward", index), operationId("deposit", index))
                .join();
        var player = store.getOrCreate(playerId, CURRENCY).join();
        var recipient = store.getOrCreate(recipientId, CURRENCY).join();

        assertNotNull(transfer);
        assertEquals(operationId("deposit", index), repeatedDeposit.operationId());
        assertEquals(SETTINGS.startingBalance().add(amount).subtract(BigDecimal.valueOf(15)), player.balance());
        assertEquals(SETTINGS.startingBalance().add(BigDecimal.valueOf(5)), recipient.balance());
    }

    private static BigDecimal amountFor(int index) {
        return BigDecimal.valueOf(50L + index % 100L);
    }

    private static UUID playerId(int index) {
        return namedUuid("player:" + index);
    }

    private static UUID recipientId(int index) {
        return namedUuid("recipient:" + index);
    }

    private static EconomyOperationId operationId(String action, int index) {
        return EconomyOperationId.of(namedUuid(action + ":" + index));
    }

    private static EconomyReason playerReason(String key, int index) {
        return EconomyReason.player(key, playerId(index));
    }

    private static UUID namedUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
