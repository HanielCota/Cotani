package com.cotani.trade.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.trade.api.TradeCurrency;
import com.cotani.trade.api.TradeId;
import com.cotani.trade.api.TradeItem;
import com.cotani.trade.api.TradeOffer;
import com.cotani.trade.api.TradeOptions;
import com.cotani.trade.api.TradeRepository;
import com.cotani.trade.api.TradeService;
import com.cotani.trade.api.TradeServiceOptions;
import com.cotani.trade.api.TradeSession;
import com.cotani.trade.api.TradeSettlement;
import com.cotani.trade.api.TradeSettlementPendingException;
import com.cotani.trade.api.TradeSettlementService;
import com.cotani.trade.api.TradeSnapshot;
import com.cotani.trade.api.TradeStateException;
import com.cotani.trade.api.TradeStatus;
import com.cotani.trade.api.TradeTimeoutScheduler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DefaultTradeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final TradeItem SWORD = new TradeItem("minecraft:diamond_sword", 1, "serialized-sword");
    private static final TradeCurrency COINS =
            new TradeCurrency(CurrencyId.of("coins"), new java.math.BigDecimal("25"));

    @Test
    void confirmsBothSidesAndSettlesOnlyOnce() {
        var settlement = new RecordingSettlementService();
        var service = service(settlement, new FixedClock(NOW));
        var created = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));

        var offeredByAlice = join(service.offerAsync(created.id(), ALICE, List.of(SWORD)));
        var offeredByBob = join(service.offerAsync(created.id(), BOB, List.of(COINS)));
        var waiting = join(service.confirmAsync(created.id(), ALICE));
        var repeated = join(service.confirmAsync(created.id(), ALICE));
        var completed = join(service.confirmAsync(created.id(), BOB));

        assertEquals(TradeStatus.OPEN, waiting.status());
        assertEquals(Set.of(ALICE), waiting.confirmations());
        assertEquals(waiting, repeated);
        assertEquals(TradeStatus.COMPLETED, completed.status());
        assertEquals(6, completed.revision());
        assertEquals(
                List.of(new TradeSettlement(
                        created.id(), offeredByAlice.initiatorOffer(), offeredByBob.recipientOffer())),
                settlement.settlements);
        assertTrue(join(service.findByPlayerAsync(ALICE)).isEmpty());
    }

    @Test
    void changingAnOfferClearsBothConfirmations() {
        var service = service(new RecordingSettlementService(), new FixedClock(NOW));
        var trade = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));

        join(service.confirmAsync(trade.id(), ALICE));
        var changed = join(service.offerAsync(trade.id(), BOB, List.of(COINS)));

        assertTrue(changed.confirmations().isEmpty());
        assertEquals(TradeStatus.OPEN, changed.status());
    }

    @Test
    void rejectsActiveParticipantConflictsAndUnauthorisedAccess() {
        var service = service(new RecordingSettlementService(), new FixedClock(NOW));
        var trade = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));

        assertThrows(
                CompletionException.class,
                () -> join(service.createAsync(ALICE, UUID.randomUUID(), TradeOptions.defaults())));
        var accessFailure = assertThrows(
                CompletionException.class,
                () -> join(service.offerAsync(trade.id(), UUID.randomUUID(), List.of(SWORD))));
        assertInstanceOf(com.cotani.trade.api.TradeAccessDeniedException.class, accessFailure.getCause());
    }

    @Test
    void marksTradeFailedWhenSettlementFails() {
        var settlement = new RecordingSettlementService();
        settlement.failure = new IllegalStateException("inventory changed");
        var service = service(settlement, new FixedClock(NOW));
        var trade = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));

        join(service.confirmAsync(trade.id(), ALICE));
        assertThrows(CompletionException.class, () -> join(service.confirmAsync(trade.id(), BOB)));

        assertEquals(
                TradeStatus.FAILED,
                join(service.findAsync(trade.id())).orElseThrow().status());
    }

    @Test
    void expiresTradesAndDoesNotBlockNewTradesForThosePlayers() {
        var clock = new MutableClock(NOW);
        var service = service(new RecordingSettlementService(), clock);
        var options = new TradeOptions(Duration.ofSeconds(1));
        var expired = join(service.createAsync(ALICE, BOB, options));
        clock.advance(Duration.ofSeconds(2));

        assertThrows(CompletionException.class, () -> join(service.cancelAsync(expired.id(), ALICE)));
        assertInstanceOf(
                TradeStateException.class,
                assertThrows(
                                CompletionException.class,
                                () -> join(service.offerAsync(expired.id(), ALICE, List.of(SWORD))))
                        .getCause());
        assertEquals(
                TradeStatus.EXPIRED,
                join(service.findAsync(expired.id())).orElseThrow().status());
        assertEquals(
                TradeStatus.OPEN, join(service.createAsync(ALICE, BOB, options)).status());
    }

    @Test
    void persistsEveryRevisionWithoutSkipping() {
        var repository = new RecordingRepository();
        var service = new DefaultTradeService(
                List.of(),
                repository,
                null,
                new RecordingSettlementService(),
                TradeServiceOptions.defaults(),
                new ImmediateTimeoutScheduler(),
                new FixedClock(NOW));
        var trade = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));
        join(service.offerAsync(trade.id(), ALICE, List.of(SWORD)));
        join(service.confirmAsync(trade.id(), ALICE));
        join(service.confirmAsync(trade.id(), BOB));

        assertEquals(List.of(0L, 1L, 2L, 3L, 4L), repository.expectedRevisions);
    }

    @Test
    void keepsSettlementPendingAfterTimeoutAndCompletesWhenSourceFinishes() {
        var settlement = new DelayedSettlementService();
        var service = new DefaultTradeService(
                List.of(),
                null,
                null,
                settlement,
                TradeServiceOptions.defaults(),
                new SettlementTimeoutScheduler(),
                new FixedClock(NOW));
        var trade = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));

        join(service.confirmAsync(trade.id(), ALICE));
        var failure = assertThrows(CompletionException.class, () -> join(service.confirmAsync(trade.id(), BOB)));

        assertInstanceOf(TradeSettlementPendingException.class, failure.getCause());
        assertEquals(
                TradeStatus.SETTLEMENT_PENDING,
                join(service.findAsync(trade.id())).orElseThrow().status());

        settlement.completion.complete(null);

        assertEquals(
                TradeStatus.COMPLETED,
                join(service.findAsync(trade.id())).orElseThrow().status());
    }

    @Test
    void keepsParticipantsReservedWhileSettlementIsPending() {
        var settlement = new DelayedSettlementService();
        var service = new DefaultTradeService(
                List.of(),
                null,
                null,
                settlement,
                TradeServiceOptions.defaults(),
                new SettlementTimeoutScheduler(),
                new FixedClock(NOW));
        var trade = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));

        join(service.confirmAsync(trade.id(), ALICE));
        assertThrows(CompletionException.class, () -> join(service.confirmAsync(trade.id(), BOB)));

        assertThrows(
                CompletionException.class,
                () -> join(service.createAsync(ALICE, UUID.randomUUID(), TradeOptions.defaults())));
    }

    @Test
    void reconcilesPendingSettlementDuringInitialization() {
        var pending = TradeSession.create(TradeId.random(), ALICE, BOB, TradeOptions.defaults(), NOW)
                .withStatus(TradeStatus.SETTLEMENT_PENDING);
        var settlement = new RecordingSettlementService();
        settlement.status = TradeStatusResult.COMPLETED;
        var service = new DefaultTradeService(
                List.of(pending),
                null,
                null,
                settlement,
                TradeServiceOptions.defaults(),
                new ImmediateTimeoutScheduler(),
                new FixedClock(NOW));

        join(service.initializeAsync());

        assertEquals(
                TradeStatus.COMPLETED,
                join(service.findAsync(pending.id())).orElseThrow().status());
    }

    @Test
    void rejectsTerminalAggregateMutations() {
        var trade = TradeSession.create(TradeId.random(), ALICE, BOB, TradeOptions.defaults(), NOW)
                .withStatus(TradeStatus.CANCELLED);

        assertThrows(IllegalStateException.class, () -> trade.withOffer(ALICE, List.of(SWORD)));
        assertThrows(IllegalStateException.class, () -> trade.withConfirmation(ALICE));
        assertThrows(IllegalStateException.class, () -> trade.withStatus(TradeStatus.OPEN));
    }

    @Test
    void rejectsDuplicateCurrenciesAndOversizedOffers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TradeOffer(
                        ALICE,
                        List.of(COINS, new TradeCurrency(CurrencyId.of("coins"), new java.math.BigDecimal("25.0")))));

        var options = TradeServiceOptions.defaults().withMaximumEncodedBytesPerParticipant(4);
        var service = new DefaultTradeService(
                List.of(),
                null,
                null,
                new RecordingSettlementService(),
                options,
                new ImmediateTimeoutScheduler(),
                new FixedClock(NOW));
        var trade = join(service.createAsync(ALICE, BOB, TradeOptions.defaults()));

        assertThrows(IllegalArgumentException.class, () -> service.offerAsync(trade.id(), ALICE, List.of(SWORD)));
    }

    @Test
    void rejectsDuplicateParticipantsInInitialState() {
        var first = TradeSession.create(TradeId.random(), ALICE, BOB, TradeOptions.defaults(), NOW);
        var second = TradeSession.create(TradeId.random(), ALICE, UUID.randomUUID(), TradeOptions.defaults(), NOW);

        assertThrows(
                IllegalArgumentException.class,
                () -> new DefaultTradeService(
                        List.of(first, second),
                        null,
                        null,
                        new RecordingSettlementService(),
                        TradeServiceOptions.defaults(),
                        new ImmediateTimeoutScheduler(),
                        new FixedClock(NOW)));
    }

    private static TradeService service(RecordingSettlementService settlement, Clock clock) {
        return new DefaultTradeService(
                List.of(),
                null,
                null,
                settlement,
                TradeServiceOptions.defaults(),
                new ImmediateTimeoutScheduler(),
                clock);
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class RecordingSettlementService implements TradeSettlementService {
        private final List<TradeSettlement> settlements = new ArrayList<>();
        private @Nullable RuntimeException failure;
        private TradeStatusResult status = TradeStatusResult.UNKNOWN;

        @Override
        public CompletionStage<Void> settleAsync(TradeSettlement settlement) {
            settlements.add(settlement);
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<com.cotani.trade.api.TradeSettlementStatus> statusAsync(TradeId tradeId) {
            return CompletableFuture.completedFuture(status.toApiStatus());
        }
    }

    private enum TradeStatusResult {
        UNKNOWN,
        COMPLETED;

        private com.cotani.trade.api.TradeSettlementStatus toApiStatus() {
            if (this == COMPLETED) {
                return com.cotani.trade.api.TradeSettlementStatus.COMPLETED;
            }
            return com.cotani.trade.api.TradeSettlementStatus.UNKNOWN;
        }
    }

    private static final class DelayedSettlementService implements TradeSettlementService {
        private final CompletableFuture<Void> completion = new CompletableFuture<>();

        @Override
        public CompletionStage<Void> settleAsync(TradeSettlement settlement) {
            return completion;
        }
    }

    private static class ImmediateTimeoutScheduler implements TradeTimeoutScheduler {
        @Override
        public <T> CompletionStage<T> withTimeout(CompletionStage<T> stage, Duration timeout, String operationName) {
            return stage;
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class SettlementTimeoutScheduler extends ImmediateTimeoutScheduler {
        @Override
        public <T> CompletionStage<T> withTimeout(CompletionStage<T> stage, Duration timeout, String operationName) {
            if (operationName.equals("settlement")) {
                return CompletableFuture.failedFuture(new TimeoutException("test timeout"));
            }
            return stage;
        }
    }

    private static final class RecordingRepository implements TradeRepository {
        private final Map<TradeId, TradeSession> trades = new HashMap<>();
        private final List<Long> expectedRevisions = new ArrayList<>();

        @Override
        public CompletionStage<TradeSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(new TradeSnapshot(List.copyOf(trades.values())));
        }

        @Override
        public CompletionStage<Void> createAsync(TradeSession trade) {
            trades.put(trade.id(), trade);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> updateAsync(TradeId tradeId, long expectedRevision, TradeSession trade) {
            expectedRevisions.add(expectedRevision);
            var current = trades.get(tradeId);
            if (current == null || current.revision() != expectedRevision || trade.revision() != expectedRevision + 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("revision mismatch"));
            }
            trades.put(tradeId, trade);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class FixedClock extends Clock {
        Instant instant;

        private FixedClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class MutableClock extends FixedClock {
        private MutableClock(Instant instant) {
            super(instant);
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
