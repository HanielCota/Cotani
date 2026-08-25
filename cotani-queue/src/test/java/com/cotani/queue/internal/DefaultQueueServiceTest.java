package com.cotani.queue.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import com.cotani.event.subscription.EventSubscription;
import com.cotani.queue.CotaniQueues;
import com.cotani.queue.api.QueueEntryOptions;
import com.cotani.queue.api.QueueId;
import com.cotani.queue.api.QueueMatch;
import com.cotani.queue.api.QueueRepository;
import com.cotani.queue.api.QueueService;
import com.cotani.queue.api.QueueServiceOptions;
import com.cotani.queue.api.QueueSnapshot;
import com.cotani.queue.api.QueueTicket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DefaultQueueServiceTest {
    private static final QueueId DUEL = QueueId.of("duel");
    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void ordersEntriesByPriorityThenSequenceAndMatchesAtomically() {
        var service = service(null, new FixedClock(NOW));
        var defaultOptions = QueueEntryOptions.defaults();

        var alice = join(service.enqueueAsync(DUEL, ALICE, defaultOptions));
        var bob = join(service.enqueueAsync(DUEL, BOB, defaultOptions.withPriority(10)));
        var carol = join(service.enqueueAsync(DUEL, CAROL, defaultOptions));

        assertEquals(List.of(bob, alice, carol), join(service.entriesAsync(DUEL)));
        var match = join(service.matchAsync(DUEL, 2)).orElseThrow();

        assertEquals(List.of(bob, alice), match.tickets());
        assertEquals(List.of(carol), join(service.entriesAsync(DUEL)));
    }

    @Test
    void limitsCapacityAndPreventsDuplicatePlayerTickets() {
        var options = QueueServiceOptions.defaults().withMaximumEntriesPerQueue(2);
        var service = new DefaultQueueService(QueueSnapshot.empty(), null, null, options, new FixedClock(NOW));

        join(service.enqueueAsync(DUEL, ALICE, QueueEntryOptions.defaults()));
        assertThrows(
                CompletionException.class,
                () -> join(service.enqueueAsync(QueueId.of("ranked"), ALICE, QueueEntryOptions.defaults())));
        join(service.enqueueAsync(DUEL, BOB, QueueEntryOptions.defaults()));
        assertThrows(
                CompletionException.class, () -> join(service.enqueueAsync(DUEL, CAROL, QueueEntryOptions.defaults())));
    }

    @Test
    void ignoresExpiredTicketsAndRemovesThemOnNextMutation() {
        var clock = new MutableClock(NOW);
        var repository = new RecordingRepository();
        var service =
                new DefaultQueueService(QueueSnapshot.empty(), repository, null, QueueServiceOptions.defaults(), clock);
        var expiringOptions = QueueEntryOptions.defaults().withLifetime(Duration.ofSeconds(1));
        join(service.enqueueAsync(DUEL, ALICE, expiringOptions));
        clock.advance(Duration.ofSeconds(2));

        assertTrue(join(service.entriesAsync(DUEL)).isEmpty());
        join(service.enqueueAsync(DUEL, BOB, QueueEntryOptions.defaults()));

        assertEquals(List.of(0L, 1L), repository.expectedRevisions);
        assertEquals(
                List.of(BOB),
                join(service.entriesAsync(DUEL)).stream()
                        .map(QueueTicket::playerId)
                        .toList());
    }

    @Test
    void usesExpectedRevisionAndDoesNotExposeRepositoryFailures() {
        var repository = new RecordingRepository();
        var service = join(CotaniQueues.fromRepositoryAsync(repository));

        join(service.enqueueAsync(DUEL, ALICE, QueueEntryOptions.defaults()));

        assertEquals(List.of(0L), repository.expectedRevisions);
        assertEquals(1, repository.snapshot.revision());

        var failedService = join(CotaniQueues.fromRepositoryAsync(new FailingRepository()));
        assertThrows(
                CompletionException.class,
                () -> join(failedService.enqueueAsync(DUEL, BOB, QueueEntryOptions.defaults())));
        assertTrue(join(failedService.entriesAsync(DUEL)).isEmpty());
    }

    @Test
    void eventFailureDoesNotUndoCommittedQueueState() {
        var service = service(new FailingEventBus(), new FixedClock(NOW));

        var ticket = join(service.enqueueAsync(DUEL, ALICE, QueueEntryOptions.defaults()));

        assertEquals(ticket, join(service.findByPlayerAsync(ALICE)).orElseThrow());
    }

    @Test
    void closeWaitsForOperationsAcceptedBeforeClose() {
        var repository = new BlockingRepository();
        var service = join(CotaniQueues.fromRepositoryAsync(repository));

        var first = service.enqueueAsync(DUEL, ALICE, QueueEntryOptions.defaults());
        var second = service.enqueueAsync(DUEL, BOB, QueueEntryOptions.defaults());
        var close = service.closeAsync();
        repository.completeFirstSave();

        join(first);
        join(second);
        join(close);
    }

    @Test
    void queriesWaitForPreviouslyAcceptedMutations() {
        var repository = new BlockingRepository();
        var service = join(CotaniQueues.fromRepositoryAsync(repository));

        var enqueue = service.enqueueAsync(DUEL, ALICE, QueueEntryOptions.defaults());
        var entries = service.entriesAsync(DUEL);

        assertFalse(entries.toCompletableFuture().isDone());
        repository.completeFirstSave();

        var ticket = join(enqueue);
        assertEquals(List.of(ticket), join(entries));
    }

    @Test
    void closeIsIdempotentAndRejectsNewOperations() {
        var service = service(null, new FixedClock(NOW));

        var close = service.closeAsync();
        join(close);

        assertSame(close, service.closeAsync());
        assertThrows(CompletionException.class, () -> join(service.entriesAsync(DUEL)));
    }

    @Test
    void acceptsCompletionStagesThatCannotBeConvertedToCompletableFuture() {
        var repository = new NonConvertibleRepository();
        var service = join(CotaniQueues.fromRepositoryAsync(repository));

        var ticket = join(service.enqueueAsync(DUEL, ALICE, QueueEntryOptions.defaults()));

        assertEquals(ticket, join(service.findByPlayerAsync(ALICE)).orElseThrow());
    }

    @Test
    void timesOutRepositoryLoad() {
        var options = new QueueServiceOptions(10, Duration.ofMillis(50), Duration.ofMillis(50));

        assertThrows(
                CompletionException.class,
                () -> join(CotaniQueues.fromRepositoryAsync(new NeverCompletingRepository(), null, options)));
    }

    @Test
    void eventTimeoutDoesNotUndoCommittedState() {
        var options = new QueueServiceOptions(10, Duration.ofSeconds(1), Duration.ofMillis(50));
        var service = new DefaultQueueService(
                QueueSnapshot.empty(), null, new NeverCompletingEventBus(), options, new FixedClock(NOW));

        var ticket = join(service.enqueueAsync(DUEL, ALICE, QueueEntryOptions.defaults()));

        assertEquals(ticket, join(service.findByPlayerAsync(ALICE)).orElseThrow());
    }

    @Test
    void rejectsDuplicateTicketIdsInMatches() {
        var sharedTicketId = UUID.randomUUID();
        var first = new QueueTicket(sharedTicketId, DUEL, ALICE, 0, NOW, NOW.plusSeconds(30), 0);
        var second = new QueueTicket(sharedTicketId, DUEL, BOB, 0, NOW, NOW.plusSeconds(30), 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> new QueueMatch(UUID.randomUUID(), DUEL, List.of(first, second), NOW));
    }

    @Test
    void validatesQueueIdsAndMatchSize() {
        assertEquals("duel.arena", QueueId.of(" DUEL.ARENA ").value());
        assertThrows(IllegalArgumentException.class, () -> QueueId.of("duel arena"));

        var service = service(null, new FixedClock(NOW));
        assertThrows(IllegalArgumentException.class, () -> join(service.matchAsync(DUEL, 1)));
    }

    private static QueueService service(@Nullable EventBus eventBus, Clock clock) {
        return new DefaultQueueService(QueueSnapshot.empty(), null, eventBus, QueueServiceOptions.defaults(), clock);
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static class RecordingRepository implements QueueRepository {
        private QueueSnapshot snapshot = QueueSnapshot.empty();
        private final List<Long> expectedRevisions = new ArrayList<>();

        @Override
        public CompletionStage<QueueSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(snapshot);
        }

        @Override
        public CompletionStage<Void> saveAsync(QueueSnapshot next, long expectedRevision) {
            expectedRevisions.add(expectedRevision);
            if (snapshot.revision() != expectedRevision) {
                return CompletableFuture.failedFuture(new IllegalStateException("unexpected revision"));
            }
            snapshot = next;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class BlockingRepository extends RecordingRepository {
        private final CompletableFuture<Void> firstSave = new CompletableFuture<>();
        private boolean first = true;

        @Override
        public CompletionStage<Void> saveAsync(QueueSnapshot next, long expectedRevision) {
            if (!first) {
                return super.saveAsync(next, expectedRevision);
            }
            first = false;
            return firstSave.thenCompose(ignored -> super.saveAsync(next, expectedRevision));
        }

        private void completeFirstSave() {
            firstSave.complete(null);
        }
    }

    private static final class FailingRepository implements QueueRepository {
        @Override
        public CompletionStage<QueueSnapshot> loadAsync() {
            return CompletableFuture.completedFuture(QueueSnapshot.empty());
        }

        @Override
        public CompletionStage<Void> saveAsync(QueueSnapshot snapshot, long expectedRevision) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"));
        }
    }

    private static final class NonConvertibleRepository implements QueueRepository {
        @Override
        public CompletionStage<QueueSnapshot> loadAsync() {
            var stage = new NonConvertibleFuture<QueueSnapshot>();
            stage.complete(QueueSnapshot.empty());
            return stage;
        }

        @Override
        public CompletionStage<Void> saveAsync(QueueSnapshot snapshot, long expectedRevision) {
            var stage = new NonConvertibleFuture<Void>();
            stage.complete(null);
            return stage;
        }
    }

    private static final class NeverCompletingRepository implements QueueRepository {
        @Override
        public CompletionStage<QueueSnapshot> loadAsync() {
            return new CompletableFuture<>();
        }

        @Override
        public CompletionStage<Void> saveAsync(QueueSnapshot snapshot, long expectedRevision) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class NonConvertibleFuture<T> extends CompletableFuture<T> {
        @Override
        public CompletableFuture<T> toCompletableFuture() {
            throw new UnsupportedOperationException("conversion is not supported");
        }
    }

    private static class FailingEventBus implements EventBus {
        @Override
        public <T extends CotaniEvent> T publish(T event) {
            return event;
        }

        @Override
        public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
            return CompletableFuture.failedFuture(new IllegalStateException("event bus unavailable"));
        }

        @Override
        public <T extends CotaniEvent> EventSubscription subscribe(
                Class<T> eventType, EventListener<? super T> listener) {
            return new NoopSubscription();
        }

        @Override
        public <T extends CotaniEvent> EventSubscription subscribe(
                Class<T> eventType, EventPriority priority, EventListener<? super T> listener) {
            return new NoopSubscription();
        }

        @Override
        public <T extends CotaniEvent> EventSubscription subscribe(
                Class<T> eventType,
                EventPriority priority,
                boolean ignoreCancelled,
                EventListener<? super T> listener) {
            return new NoopSubscription();
        }

        @Override
        public void unsubscribe(EventSubscription subscription) {}

        @Override
        public void clear() {}
    }

    private static final class NeverCompletingEventBus extends FailingEventBus {
        @Override
        public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
            return new CompletableFuture<>();
        }
    }

    private static final class NoopSubscription implements EventSubscription {
        @Override
        public UUID id() {
            return UUID.randomUUID();
        }

        @Override
        public Class<? extends CotaniEvent> eventType() {
            return CotaniEvent.class;
        }

        @Override
        public EventPriority priority() {
            return EventPriority.NORMAL;
        }

        @Override
        public EventListener<? extends CotaniEvent> listener() {
            return ignored -> {};
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void unsubscribe() {}
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
