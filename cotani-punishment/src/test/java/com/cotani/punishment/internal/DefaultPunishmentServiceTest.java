package com.cotani.punishment.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.audit.api.AuditActor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditQuery;
import com.cotani.audit.api.AuditService;
import com.cotani.punishment.CotaniPunishments;
import com.cotani.punishment.api.Punishment;
import com.cotani.punishment.api.PunishmentCursor;
import com.cotani.punishment.api.PunishmentId;
import com.cotani.punishment.api.PunishmentQuery;
import com.cotani.punishment.api.PunishmentRepository;
import com.cotani.punishment.api.PunishmentRequest;
import com.cotani.punishment.api.PunishmentServiceOptions;
import com.cotani.punishment.api.PunishmentType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class DefaultPunishmentServiceTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void appliesAndQueriesOnlyActivePunishments() {
        var service = CotaniPunishments.inMemory();
        var active = request(PunishmentType.MUTE, Optional.of(CREATED_AT.plus(Duration.ofHours(1))));
        var expired = request(PunishmentType.BAN, CREATED_AT.minusSeconds(2), Optional.of(CREATED_AT.minusSeconds(1)));

        service.applyAsync(active).toCompletableFuture().join();
        service.applyAsync(expired).toCompletableFuture().join();

        var values = service.queryAsync(com.cotani.punishment.api.PunishmentQuery.builder()
                        .targetId(TARGET)
                        .activeAt(CREATED_AT)
                        .build())
                .toCompletableFuture()
                .join();

        assertEquals(List.of(active.toPunishment()), values);
    }

    @Test
    void doesNotTreatPunishmentAsActiveBeforeCreationOrBeforeRevocation() {
        var service = CotaniPunishments.inMemory();
        var request = request(PunishmentType.BAN, CREATED_AT, Optional.of(CREATED_AT.plusSeconds(30)));
        var punishment = service.applyAsync(request).toCompletableFuture().join();
        var revocation = new Punishment.Revocation(AuditActor.system(), "appeal accepted", CREATED_AT.plusSeconds(10));
        var revoked = service.revokeAsync(punishment.id(), revocation)
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertEquals(
                com.cotani.punishment.api.PunishmentStatus.NOT_STARTED, revoked.statusAt(CREATED_AT.minusSeconds(1)));
        assertEquals(com.cotani.punishment.api.PunishmentStatus.ACTIVE, revoked.statusAt(CREATED_AT.plusSeconds(5)));
        assertEquals(com.cotani.punishment.api.PunishmentStatus.REVOKED, revoked.statusAt(CREATED_AT.plusSeconds(10)));
    }

    @Test
    void paginatesInStableDescendingOrder() {
        var service = CotaniPunishments.inMemory();
        var oldest = request(PunishmentType.WARN, CREATED_AT, Optional.empty());
        var newest = request(PunishmentType.WARN, CREATED_AT.plusSeconds(1), Optional.empty());
        service.applyAsync(oldest).toCompletableFuture().join();
        service.applyAsync(newest).toCompletableFuture().join();

        var firstPage = service.queryAsync(PunishmentQuery.builder().limit(1).build())
                .toCompletableFuture()
                .join();
        var secondPage = service.queryAsync(PunishmentQuery.builder()
                        .before(PunishmentCursor.from(firstPage.get(0)))
                        .limit(1)
                        .build())
                .toCompletableFuture()
                .join();

        assertEquals(List.of(newest.toPunishment()), firstPage);
        assertEquals(List.of(oldest.toPunishment()), secondPage);
    }

    @Test
    void usesTheSameStringCursorOrderingAsStorage() {
        var service = CotaniPunishments.inMemory();
        var low = request(
                new PunishmentId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                PunishmentType.WARN,
                CREATED_AT,
                Optional.empty());
        var high = request(
                new PunishmentId(UUID.fromString("80000000-0000-0000-0000-000000000001")),
                PunishmentType.WARN,
                CREATED_AT,
                Optional.empty());
        service.applyAsync(low).toCompletableFuture().join();
        service.applyAsync(high).toCompletableFuture().join();

        var page = service.queryAsync(PunishmentQuery.builder().limit(1).build())
                .toCompletableFuture()
                .join();

        assertEquals(List.of(high.toPunishment()), page);
    }

    @Test
    void reusesAnIdenticalIdempotencyRequestAndRejectsConflicts() {
        var service = CotaniPunishments.inMemory();
        var request = request(PunishmentType.WARN, Optional.empty());

        assertEquals(
                request.toPunishment(),
                service.applyAsync(request).toCompletableFuture().join());
        assertEquals(
                request.toPunishment(),
                service.applyAsync(request).toCompletableFuture().join());

        var conflict = new PunishmentRequest(
                request.id(),
                request.targetId(),
                request.actor(),
                PunishmentType.BAN,
                request.reason(),
                request.createdAt(),
                request.expiresAt());
        assertThrows(
                CompletionException.class,
                () -> service.applyAsync(conflict).toCompletableFuture().join());
    }

    @Test
    void persistsBeforePublishingTheMutationAndSupportsRevocation() {
        var repository = new RecordingRepository();
        var service = CotaniPunishments.fromRepositoryAsync(repository)
                .toCompletableFuture()
                .join();
        var request = request(PunishmentType.BAN, Optional.empty());
        var punishment = service.applyAsync(request).toCompletableFuture().join();
        var revocation = new Punishment.Revocation(AuditActor.system(), "appeal accepted", CREATED_AT.plusSeconds(1));

        var revoked = service.revokeAsync(punishment.id(), revocation)
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertTrue(revoked.revocation().isPresent());
        assertEquals(2, repository.saveCount);
        assertEquals(List.of(revoked), repository.saved);
        assertThrows(
                CompletionException.class,
                () -> service.revokeAsync(
                                punishment.id(),
                                new Punishment.Revocation(
                                        AuditActor.system(), "different reason", CREATED_AT.plusSeconds(2)))
                        .toCompletableFuture()
                        .join());
    }

    @Test
    void rejectsOperationsAfterClose() {
        var service = CotaniPunishments.inMemory();
        service.closeAsync().toCompletableFuture().join();

        assertThrows(
                CompletionException.class,
                () -> service.queryAsync(com.cotani.punishment.api.PunishmentQuery.all())
                        .toCompletableFuture()
                        .join());
    }

    @Test
    void closePropagatesTheLastPersistenceFailure() {
        var service = CotaniPunishments.fromRepositoryAsync(new FailingRepository())
                .toCompletableFuture()
                .join();

        var operation = service.applyAsync(request(PunishmentType.BAN, Optional.empty()));

        assertThrows(
                CompletionException.class, () -> operation.toCompletableFuture().join());
        assertThrows(
                CompletionException.class,
                () -> service.closeAsync().toCompletableFuture().join());
    }

    @Test
    void repositoryStateRemainsQueryableWhenAuditRetryFails() {
        var repository = new RecordingRepository();
        var service = CotaniPunishments.fromRepositoryAsync(
                        repository, PunishmentServiceOptions.defaults(), new FailingAuditService())
                .toCompletableFuture()
                .join();
        var request = request(PunishmentType.MUTE, Optional.empty());

        assertThrows(
                CompletionException.class,
                () -> service.applyAsync(request).toCompletableFuture().join());
        assertEquals(
                List.of(request.toPunishment()),
                service.queryAsync(PunishmentQuery.all()).toCompletableFuture().join());
    }

    @Test
    void timesOutRepositorySave() {
        var service = CotaniPunishments.fromRepositoryAsync(
                        new PendingRepository(), new PunishmentServiceOptions(Duration.ofMillis(20)), null)
                .toCompletableFuture()
                .join();

        var stage = service.applyAsync(request(PunishmentType.MUTE, Optional.empty()));

        assertThrows(RuntimeException.class, () -> stage.toCompletableFuture().join());
        assertTrue(service.queryAsync(com.cotani.punishment.api.PunishmentQuery.all())
                .toCompletableFuture()
                .join()
                .isEmpty());
    }

    private static PunishmentRequest request(PunishmentType type, Optional<Instant> expiresAt) {
        return request(type, CREATED_AT, expiresAt);
    }

    private static PunishmentRequest request(PunishmentType type, Instant createdAt, Optional<Instant> expiresAt) {
        return request(PunishmentId.random(), type, createdAt, expiresAt);
    }

    private static PunishmentRequest request(
            PunishmentId id, PunishmentType type, Instant createdAt, Optional<Instant> expiresAt) {
        return new PunishmentRequest(id, TARGET, AuditActor.system(), type, "moderation", createdAt, expiresAt);
    }

    private static final class RecordingRepository implements PunishmentRepository {
        private final List<Punishment> saved = new ArrayList<>();
        private int saveCount;

        @Override
        public CompletionStage<Optional<Punishment>> findAsync(PunishmentId id) {
            return CompletableFuture.completedFuture(
                    saved.stream().filter(value -> value.id().equals(id)).findFirst());
        }

        @Override
        public CompletionStage<List<Punishment>> queryAsync(PunishmentQuery query) {
            return CompletableFuture.completedFuture(saved.stream()
                    .filter(value -> query.targetId()
                            .map(target -> target.equals(value.targetId()))
                            .orElse(true))
                    .filter(value ->
                            query.type().map(type -> type == value.type()).orElse(true))
                    .filter(value -> query.activeAt().map(value::isActiveAt).orElse(true))
                    .sorted(java.util.Comparator.comparing(Punishment::createdAt)
                            .reversed())
                    .limit(query.limit())
                    .toList());
        }

        @Override
        public CompletionStage<Void> saveAsync(Punishment punishment) {
            saveCount++;
            saved.removeIf(value -> value.id().equals(punishment.id()));
            saved.add(punishment);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class PendingRepository implements PunishmentRepository {
        @Override
        public CompletionStage<Optional<Punishment>> findAsync(PunishmentId id) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<List<Punishment>> queryAsync(PunishmentQuery query) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Void> saveAsync(Punishment punishment) {
            return new CompletableFuture<>();
        }
    }

    private static final class FailingRepository implements PunishmentRepository {
        @Override
        public CompletionStage<Optional<Punishment>> findAsync(PunishmentId id) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<List<Punishment>> queryAsync(PunishmentQuery query) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Void> saveAsync(Punishment punishment) {
            return CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"));
        }
    }

    private static final class FailingAuditService implements AuditService {
        @Override
        public CompletionStage<Void> recordAsync(AuditEntry entry) {
            return CompletableFuture.failedFuture(new IllegalStateException("audit unavailable"));
        }

        @Override
        public CompletionStage<List<AuditEntry>> findAsync(AuditQuery query) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Void> closeAsync() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
