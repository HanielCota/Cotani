package com.cotani.punishment.internal;

import com.cotani.api.InternalApi;
import com.cotani.audit.api.AuditAction;
import com.cotani.audit.api.AuditActor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditService;
import com.cotani.audit.api.AuditSeverity;
import com.cotani.audit.api.AuditTarget;
import com.cotani.punishment.api.Punishment;
import com.cotani.punishment.api.PunishmentConflictException;
import com.cotani.punishment.api.PunishmentCursor;
import com.cotani.punishment.api.PunishmentId;
import com.cotani.punishment.api.PunishmentQuery;
import com.cotani.punishment.api.PunishmentRepository;
import com.cotani.punishment.api.PunishmentRequest;
import com.cotani.punishment.api.PunishmentService;
import com.cotani.punishment.api.PunishmentServiceOptions;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultPunishmentService implements PunishmentService {
    private static final Comparator<Punishment> NEWEST_FIRST = Comparator.comparing(Punishment::createdAt)
            .reversed()
            .thenComparing(value -> value.id().value().toString(), Comparator.reverseOrder());

    private final Map<PunishmentId, Punishment> punishments = new LinkedHashMap<>();
    private final @Nullable PunishmentRepository repository;
    private final @Nullable AuditService auditService;
    private final PunishmentServiceOptions options;
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();

    public DefaultPunishmentService(
            List<Punishment> initialPunishments,
            @Nullable PunishmentRepository repository,
            @Nullable AuditService auditService,
            PunishmentServiceOptions options) {
        this.repository = repository;
        this.auditService = auditService;
        this.options = Objects.requireNonNull(options, "options");
        Objects.requireNonNull(initialPunishments, "initialPunishments").forEach(punishment -> {
            var value = Objects.requireNonNull(punishment, "initial punishment");
            var previous = punishments.put(value.id(), value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalArgumentException(
                        "Duplicate punishment ID: " + value.id().value());
            }
        });
    }

    @Override
    public CompletionStage<Punishment> applyAsync(PunishmentRequest request) {
        Objects.requireNonNull(request, "request");
        var candidate = request.toPunishment();
        return enqueue(() -> persistNewAsync(candidate));
    }

    @Override
    public CompletionStage<Optional<Punishment>> findAsync(PunishmentId id) {
        Objects.requireNonNull(id, "id");
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var pendingWrites = sequencingTail;
            if (repository != null) {
                return pendingWrites.thenCompose(_ -> findFromRepositoryAsync(id));
            }
            return pendingWrites.thenApply(_ -> {
                synchronized (lifecycleLock) {
                    return Optional.ofNullable(punishments.get(id));
                }
            });
        }
    }

    @Override
    public CompletionStage<List<Punishment>> queryAsync(PunishmentQuery query) {
        Objects.requireNonNull(query, "query");
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var pendingWrites = sequencingTail;
            if (repository != null) {
                return pendingWrites.thenCompose(_ -> queryFromRepositoryAsync(query));
            }
            return pendingWrites.thenApply(_ -> querySnapshot(query));
        }
    }

    @Override
    public CompletionStage<Optional<Punishment>> revokeAsync(PunishmentId id, Punishment.Revocation revocation) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(revocation, "revocation");
        return enqueue(() -> persistRevocationAsync(id, revocation));
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            closed.compareAndSet(false, true);
            return lastOperation;
        }
    }

    private <T> CompletionStage<T> enqueue(Supplier<CompletionStage<T>> operation) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var current = sequencingTail;
            var submitted = current.handle((_, _) -> null)
                    .thenCompose(_ -> Objects.requireNonNull(operation.get(), "operation stage"));
            sequencingTail = submitted.handle((_, _) -> null);
            lastOperation = submitted.thenApply(_ -> null);
            return submitted;
        }
    }

    private CompletionStage<Punishment> persistNewAsync(Punishment candidate) {
        if (repository != null) {
            return findFromRepositoryAsync(candidate.id()).thenCompose(existing -> {
                if (existing.isPresent()) {
                    return sameOrConflict(existing.orElseThrow(), candidate);
                }
                return saveAndAuditAsync(
                        candidate, candidate.actor(), candidate.createdAt(), candidate.reason(), "punishment.apply");
            });
        }

        synchronized (lifecycleLock) {
            var existing = punishments.get(candidate.id());
            if (existing != null) {
                return sameOrConflict(existing, candidate);
            }
            return saveAndAuditAsync(
                            candidate, candidate.actor(), candidate.createdAt(), candidate.reason(), "punishment.apply")
                    .thenApply(value -> {
                        synchronized (lifecycleLock) {
                            punishments.put(value.id(), value);
                        }
                        return value;
                    });
        }
    }

    private CompletionStage<Punishment> sameOrConflict(Punishment existing, Punishment candidate) {
        if (existing.equals(candidate)) {
            return auditAsync(
                            candidate, candidate.actor(), candidate.createdAt(), candidate.reason(), "punishment.apply")
                    .thenApply(_ -> existing);
        }
        return failed(new PunishmentConflictException(candidate.id()));
    }

    private CompletionStage<Optional<Punishment>> persistRevocationAsync(
            PunishmentId id, Punishment.Revocation revocation) {
        if (repository != null) {
            return findFromRepositoryAsync(id)
                    .thenCompose(current -> current.map(value -> revokeExistingAsync(value, revocation))
                            .orElseGet(() -> completed(Optional.empty())));
        }

        synchronized (lifecycleLock) {
            var current = punishments.get(id);
            if (current == null) {
                return completed(Optional.empty());
            }
            return revokeExistingAsync(current, revocation).thenApply(result -> {
                result.ifPresent(value -> {
                    synchronized (lifecycleLock) {
                        punishments.put(value.id(), value);
                    }
                });
                return result;
            });
        }
    }

    private CompletionStage<Optional<Punishment>> revokeExistingAsync(
            Punishment current, Punishment.Revocation revocation) {
        if (current.revocation().isPresent()) {
            var existingRevocation = current.revocation().orElseThrow();
            if (!existingRevocation.equals(revocation)) {
                return failed(new PunishmentConflictException(current.id()));
            }
            return auditAsync(
                            current,
                            existingRevocation.actor(),
                            existingRevocation.revokedAt(),
                            existingRevocation.reason(),
                            "punishment.revoke")
                    .thenApply(_ -> Optional.of(current));
        }

        var revoked = current.revoke(revocation);
        return saveAndAuditAsync(
                        revoked, revocation.actor(), revocation.revokedAt(), revocation.reason(), "punishment.revoke")
                .thenApply(value -> Optional.of(value));
    }

    private CompletionStage<Punishment> saveAndAuditAsync(
            Punishment punishment, AuditActor actor, Instant occurredAt, String detail, String action) {
        return persistAsync(punishment)
                .thenCompose(_ -> auditAsync(punishment, actor, occurredAt, detail, action))
                .thenApply(_ -> punishment);
    }

    private CompletionStage<Optional<Punishment>> findFromRepositoryAsync(PunishmentId id) {
        var currentRepository = Objects.requireNonNull(repository, "repository");
        return options.withTimeout(Objects.requireNonNull(currentRepository.findAsync(id), "repository find stage"));
    }

    private CompletionStage<List<Punishment>> queryFromRepositoryAsync(PunishmentQuery query) {
        var currentRepository = Objects.requireNonNull(repository, "repository");
        return options.withTimeout(
                        Objects.requireNonNull(currentRepository.queryAsync(query), "repository query stage"))
                .thenApply(List::copyOf);
    }

    private CompletionStage<Void> persistAsync(Punishment punishment) {
        if (repository == null) {
            return completedVoid();
        }
        return options.withTimeout(Objects.requireNonNull(repository.saveAsync(punishment), "repository save stage"));
    }

    private CompletionStage<Void> auditAsync(
            Punishment punishment, AuditActor actor, Instant occurredAt, String detail, String action) {
        if (auditService == null) {
            return completedVoid();
        }
        var details = new LinkedHashMap<String, String>();
        details.put("punishment_id", punishment.id().value().toString());
        details.put("punishment_type", punishment.type().name());
        details.put("reason", detail);
        return Objects.requireNonNull(
                auditService.recordAsync(new AuditEntry(
                        UUID.nameUUIDFromBytes(
                                (action + "\u0000" + punishment.id().value()).getBytes(StandardCharsets.UTF_8)),
                        occurredAt,
                        actor,
                        AuditAction.of(action),
                        AuditTarget.of("player", punishment.targetId().toString()),
                        punishment.type() == com.cotani.punishment.api.PunishmentType.BAN
                                ? AuditSeverity.CRITICAL
                                : AuditSeverity.WARNING,
                        details)),
                "audit stage");
    }

    private List<Punishment> querySnapshot(PunishmentQuery query) {
        synchronized (lifecycleLock) {
            var values = new ArrayList<>(punishments.values());
            values.sort(NEWEST_FIRST);
            return values.stream()
                    .filter(value -> query.targetId()
                            .map(target -> target.equals(value.targetId()))
                            .orElse(true))
                    .filter(value ->
                            query.type().map(type -> type == value.type()).orElse(true))
                    .filter(value -> query.activeAt().map(value::isActiveAt).orElse(true))
                    .filter(value -> query.before()
                            .map(cursor -> isBefore(value, cursor))
                            .orElse(true))
                    .limit(query.limit())
                    .toList();
        }
    }

    private static boolean isBefore(Punishment punishment, PunishmentCursor cursor) {
        return punishment.createdAt().isBefore(cursor.createdAt())
                || (punishment.createdAt().equals(cursor.createdAt())
                        && punishment
                                        .id()
                                        .value()
                                        .toString()
                                        .compareTo(cursor.id().value().toString())
                                < 0);
    }

    private static <T> CompletionStage<T> completed(T value) {
        return CompletableFuture.completedFuture(value);
    }

    @SuppressWarnings("NullAway")
    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Punishment service is closed");
    }
}
