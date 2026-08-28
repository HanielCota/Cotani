package com.cotani.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.audit.api.AuditAction;
import com.cotani.audit.api.AuditActor;
import com.cotani.audit.api.AuditCapacityExceededException;
import com.cotani.audit.api.AuditCursor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditQuery;
import com.cotani.audit.api.AuditRepository;
import com.cotani.audit.api.AuditSeverity;
import com.cotani.audit.api.AuditTarget;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class AuditServiceTest {
    @Test
    @Tag("stress")
    void recordsAndQueriesOneThousandGeneratedAuditEntriesInStableOrder() {
        var service = CotaniAudits.inMemory(2_000);
        var action = AuditAction.of("stress.player.action");
        try {
            StressTestSupport.scenarios("audit", "record-query-order", (context, random, player) -> {
                var occurredAt = Instant.EPOCH.plusMillis(context.iteration());
                var entry = new AuditEntry(
                        random.uuid("entry"),
                        occurredAt,
                        AuditActor.player(player.id()),
                        action,
                        AuditTarget.resource("player", player.id().toString()),
                        AuditSeverity.INFO,
                        Map.of("iteration", Integer.toString(context.iteration())));
                StressTestSupport.await(service.recordAsync(entry), Duration.ofSeconds(30), context);
            });

            var page = service.findAsync(
                            AuditQuery.builder().action(action).limit(1_000).build())
                    .toCompletableFuture()
                    .join();
            assertEquals(1_000, page.size());
            assertEquals(
                    StressTestSupport.iterations() - 1L,
                    page.getFirst().occurredAt().toEpochMilli());
            assertTrue(java.util.stream.IntStream.range(1, page.size())
                    .allMatch(index -> !page.get(index - 1)
                            .occurredAt()
                            .isBefore(page.get(index).occurredAt())));
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }

    @Test
    void recordsEntriesAndReturnsNewestFirstWithFilters() {
        var service = CotaniAudits.inMemory();
        var playerId = UUID.randomUUID();
        var older = entry("permission.group.assign", playerId, Instant.parse("2025-01-01T00:00:00Z"));
        var newer = entry("permission.group.remove", playerId, Instant.parse("2025-01-02T00:00:00Z"));

        service.recordAsync(older).toCompletableFuture().join();
        service.recordAsync(newer).toCompletableFuture().join();

        var all = service.findAsync(AuditQuery.all()).toCompletableFuture().join();
        var filtered = service.findAsync(AuditQuery.builder()
                        .action(AuditAction.of("permission.group.assign"))
                        .limit(10)
                        .build())
                .toCompletableFuture()
                .join();
        var nextPage = service.findAsync(AuditQuery.builder()
                        .before(AuditCursor.after(newer))
                        .limit(10)
                        .build())
                .toCompletableFuture()
                .join();

        assertEquals(List.of(newer, older), all);
        assertEquals(List.of(older), filtered);
        assertEquals(List.of(older), nextPage);
    }

    @Test
    void serializesWritesInSubmissionOrder() {
        var repository = new DelayedRepository();
        var service = CotaniAudits.fromRepository(repository);
        var first = entry("first", UUID.randomUUID(), Instant.parse("2025-01-01T00:00:00Z"));
        var second = entry("second", UUID.randomUUID(), Instant.parse("2025-01-01T00:00:01Z"));

        var firstWrite = service.recordAsync(first).toCompletableFuture();
        var secondWrite = service.recordAsync(second).toCompletableFuture();

        assertEquals(List.of(first), repository.entries());
        assertFalse(secondWrite.isDone());

        repository.completeNext();
        assertEquals(List.of(first, second), repository.entries());
        repository.completeNext();

        firstWrite.join();
        secondWrite.join();
    }

    @Test
    void rejectsOperationsAfterClose() {
        var service = CotaniAudits.inMemory();
        service.closeAsync().toCompletableFuture().join();

        var failure = assertThrows(
                CompletionException.class,
                () -> service.recordAsync(entry("closed", UUID.randomUUID(), Instant.now()))
                        .toCompletableFuture()
                        .join());

        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    @Test
    void stopsThePipelineAfterAPersistenceFailure() {
        var repository = new DelayedRepository();
        var service = CotaniAudits.fromRepository(repository);
        var first = entry("first", UUID.randomUUID(), Instant.parse("2025-01-01T00:00:00Z"));
        var second = entry("second", UUID.randomUUID(), Instant.parse("2025-01-01T00:00:01Z"));

        var firstWrite = service.recordAsync(first).toCompletableFuture();
        var secondWrite = service.recordAsync(second).toCompletableFuture();
        repository.failNext(new IllegalStateException("database unavailable"));

        assertThrows(CompletionException.class, firstWrite::join);
        assertThrows(CompletionException.class, secondWrite::join);
        assertThrows(
                CompletionException.class,
                () -> service.findAsync(AuditQuery.all()).toCompletableFuture().join());
        assertEquals(List.of(first), repository.entries());
    }

    @Test
    void enforcesTheConfiguredInMemoryCapacity() {
        var service = CotaniAudits.inMemory(1);
        service.recordAsync(entry("first", UUID.randomUUID(), Instant.EPOCH))
                .toCompletableFuture()
                .join();

        var failure = assertThrows(
                CompletionException.class,
                () -> service.recordAsync(entry("second", UUID.randomUUID(), Instant.EPOCH.plusSeconds(1)))
                        .toCompletableFuture()
                        .join());

        assertTrue(failure.getCause() instanceof AuditCapacityExceededException);
    }

    private static AuditEntry entry(String action, UUID playerId, Instant occurredAt) {
        return new AuditEntry(
                UUID.randomUUID(),
                occurredAt,
                AuditActor.player(playerId),
                AuditAction.of(action),
                AuditTarget.resource("player", playerId.toString()),
                AuditSeverity.INFO,
                Map.of("source", "test", "message", "a|value.with punctuation"));
    }

    private static final class DelayedRepository implements AuditRepository {
        private final List<AuditEntry> entries = new ArrayList<>();
        private final List<CompletableFuture<Void>> pending = new ArrayList<>();

        @Override
        public synchronized CompletionStage<Void> appendAsync(AuditEntry entry) {
            entries.add(entry);
            var completion = new CompletableFuture<Void>();
            pending.add(completion);
            return completion;
        }

        @Override
        public synchronized CompletionStage<List<AuditEntry>> queryAsync(AuditQuery query) {
            return CompletableFuture.completedFuture(List.copyOf(entries));
        }

        private synchronized List<AuditEntry> entries() {
            return List.copyOf(entries);
        }

        private synchronized void completeNext() {
            pending.remove(0).complete(null);
        }

        private synchronized void failNext(Throwable failure) {
            pending.remove(0).completeExceptionally(failure);
        }
    }
}
