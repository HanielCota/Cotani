package com.cotani.punishment.storage;

import com.cotani.audit.api.AuditActor;
import com.cotani.punishment.api.Punishment;
import com.cotani.punishment.api.PunishmentConflictException;
import com.cotani.punishment.api.PunishmentId;
import com.cotani.punishment.api.PunishmentQuery;
import com.cotani.punishment.api.PunishmentRepository;
import com.cotani.punishment.api.PunishmentType;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.Row;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL-backed punishment repository using Cotani Storage. */
public final class StoragePunishmentRepository implements PunishmentRepository {
    private final CotaniStorage storage;

    public StoragePunishmentRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<java.util.Optional<Punishment>> findAsync(PunishmentId id) {
        Objects.requireNonNull(id, "id");
        return storage.queryExecutor()
                .queryOne(
                        SELECT_COLUMNS + " FROM cotani_punishments WHERE id = ?",
                        binder -> binder.string(id.value().toString()),
                        StoragePunishmentRepository::mapPunishment);
    }

    @Override
    public CompletionStage<List<Punishment>> queryAsync(PunishmentQuery query) {
        Objects.requireNonNull(query, "query");
        var sql = new StringBuilder(SELECT_COLUMNS).append(" FROM cotani_punishments WHERE 1 = 1");
        query.targetId().ifPresent(_ -> sql.append(" AND target_id = ?"));
        query.type().ifPresent(_ -> sql.append(" AND punishment_type = ?"));
        query.activeAt()
                .ifPresent(_ -> sql.append(" AND created_at <= ? AND (expires_at IS NULL OR expires_at > ?)"
                        + " AND (revoked_at IS NULL OR revoked_at > ?)"));
        query.before().ifPresent(_ -> sql.append(" AND (created_at < ? OR (created_at = ? AND id < ?))"));
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");

        return storage.queryExecutor()
                .queryMany(
                        sql.toString(), binder -> bindQuery(binder, query), StoragePunishmentRepository::mapPunishment);
    }

    @Override
    public CompletionStage<Void> saveAsync(Punishment punishment) {
        Objects.requireNonNull(punishment, "punishment");
        return findAsync(punishment.id()).thenCompose(existing -> {
            if (existing.isEmpty()) {
                if (punishment.revocation().isPresent()) {
                    return java.util.concurrent.CompletableFuture.failedFuture(
                            new IllegalStateException("Cannot revoke a missing punishment: "
                                    + punishment.id().value()));
                }
                return insertNewAsync(punishment);
            }

            var current = existing.orElseThrow();
            if (current.equals(punishment)) {
                return completedVoid();
            }
            if (!canApplyRevocation(current, punishment)) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new PunishmentConflictException(punishment.id()));
            }
            return updateRevocationAsync(punishment)
                    .thenCompose(_ -> findAsync(punishment.id()))
                    .thenCompose(updated -> updated.filter(punishment::equals)
                            .map(_ -> completedVoid())
                            .orElseGet(() -> java.util.concurrent.CompletableFuture.failedFuture(
                                    new PunishmentConflictException(punishment.id()))));
        });
    }

    private CompletionStage<Void> insertNewAsync(Punishment punishment) {
        var revocation = punishment.revocation();
        return storage.table("cotani_punishments")
                .upsert()
                .value("id", punishment.id().value())
                .value("target_id", punishment.targetId())
                .value("actor_type", punishment.actor().type())
                .value("actor_id", punishment.actor().id())
                .value("punishment_type", punishment.type().name())
                .value("reason", punishment.reason())
                .value("created_at", punishment.createdAt())
                .value("expires_at", punishment.expiresAt().orElse(null))
                .value(
                        "revoked_at",
                        revocation.map(Punishment.Revocation::revokedAt).orElse(null))
                .value(
                        "revoked_by_type",
                        revocation.map(value -> value.actor().type()).orElse(null))
                .value(
                        "revoked_by_id",
                        revocation.map(value -> value.actor().id()).orElse(null))
                .value(
                        "revoked_reason",
                        revocation.map(Punishment.Revocation::reason).orElse(null))
                .conflict("id")
                .execute()
                .thenCompose(_ -> findAsync(punishment.id()))
                .thenCompose(saved -> saved.filter(punishment::equals)
                        .map(_ -> completedVoid())
                        .orElseGet(() -> java.util.concurrent.CompletableFuture.failedFuture(
                                new PunishmentConflictException(punishment.id()))));
    }

    private CompletionStage<Void> updateRevocationAsync(Punishment punishment) {
        var revocation = punishment.revocation().orElseThrow();
        return storage.queryExecutor()
                .update(
                        "UPDATE cotani_punishments SET revoked_at = ?, revoked_by_type = ?, revoked_by_id = ?, revoked_reason = ? WHERE id = ? AND revoked_at IS NULL",
                        binder -> binder.instant(revocation.revokedAt())
                                .string(revocation.actor().type())
                                .string(revocation.actor().id())
                                .string(revocation.reason())
                                .string(punishment.id().value().toString()));
    }

    private static boolean canApplyRevocation(Punishment current, Punishment candidate) {
        return current.revocation().isEmpty()
                && candidate.revocation().isPresent()
                && current.id().equals(candidate.id())
                && current.targetId().equals(candidate.targetId())
                && current.actor().equals(candidate.actor())
                && current.type() == candidate.type()
                && current.reason().equals(candidate.reason())
                && current.createdAt().equals(candidate.createdAt())
                && current.expiresAt().equals(candidate.expiresAt());
    }

    private static void bindQuery(com.cotani.storage.query.ParameterBinder binder, PunishmentQuery query)
            throws SQLException {
        if (query.targetId().isPresent()) {
            binder.string(query.targetId().orElseThrow().toString());
        }
        if (query.type().isPresent()) {
            binder.string(query.type().orElseThrow().name());
        }
        if (query.activeAt().isPresent()) {
            var activeAt = query.activeAt().orElseThrow();
            binder.instant(activeAt).instant(activeAt).instant(activeAt);
        }
        if (query.before().isPresent()) {
            var before = query.before().orElseThrow();
            binder.instant(before.createdAt())
                    .instant(before.createdAt())
                    .string(before.id().value().toString());
        }
        binder.integer(query.limit());
    }

    private static final String SELECT_COLUMNS =
            "SELECT id, target_id, actor_type, actor_id, punishment_type, reason, created_at, expires_at, revoked_at, revoked_by_type, revoked_by_id, revoked_reason";

    @SuppressWarnings("NullAway")
    private static CompletionStage<Void> completedVoid() {
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    public static List<Migration> migrations() {
        return List.of(new CreatePunishmentTablesMigration(), new CreatePunishmentIndexesMigration());
    }

    private static Punishment mapPunishment(Row row) throws SQLException {
        var revokedAt = row.getInstantOptional("revoked_at");
        var revokedByType = row.getStringOptional("revoked_by_type");
        var revokedById = row.getStringOptional("revoked_by_id");
        var revokedReason = row.getStringOptional("revoked_reason");
        var revocation = revokedAt.map(at -> new Punishment.Revocation(
                AuditActor.of(
                        revokedByType.orElseThrow(() -> new IllegalStateException("Missing revoked_by_type")),
                        revokedById.orElseThrow(() -> new IllegalStateException("Missing revoked_by_id"))),
                revokedReason.orElseThrow(() -> new IllegalStateException("Missing revoked_reason")),
                at));
        return new Punishment(
                new PunishmentId(UUID.fromString(row.getString("id"))),
                UUID.fromString(row.getString("target_id")),
                AuditActor.of(row.getString("actor_type"), row.getString("actor_id")),
                PunishmentType.valueOf(row.getString("punishment_type")),
                row.getString("reason"),
                row.getInstantOptional("created_at")
                        .orElseThrow(() -> new IllegalStateException("Punishment created_at is null")),
                row.getInstantOptional("expires_at"),
                revocation);
    }
}
