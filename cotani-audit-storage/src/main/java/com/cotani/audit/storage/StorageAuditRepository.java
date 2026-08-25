package com.cotani.audit.storage;

import com.cotani.audit.api.AuditAction;
import com.cotani.audit.api.AuditActor;
import com.cotani.audit.api.AuditCursor;
import com.cotani.audit.api.AuditEntry;
import com.cotani.audit.api.AuditQuery;
import com.cotani.audit.api.AuditRepository;
import com.cotani.audit.api.AuditSeverity;
import com.cotani.audit.api.AuditTarget;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import com.cotani.storage.query.Row;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Idempotent Cotani Storage implementation of the append-only audit repository. */
public final class StorageAuditRepository implements AuditRepository {
    private final CotaniStorage storage;

    public StorageAuditRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<Void> appendAsync(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return storage.table("cotani_audit_entries")
                .upsert()
                .value("id", entry.id())
                .value("occurred_at", entry.occurredAt())
                .value("actor_type", entry.actor().type())
                .value("actor_id", entry.actor().id())
                .value("action", entry.action().value())
                .value("target_type", entry.target().type())
                .value("target_id", entry.target().id())
                .value("severity", entry.severity().name())
                .value("details", AuditDetailsCodec.encode(entry.details()))
                .conflict("id")
                .execute();
    }

    @Override
    public CompletionStage<List<AuditEntry>> queryAsync(AuditQuery query) {
        Objects.requireNonNull(query, "query");
        var sql = new StringBuilder(
                "SELECT id, occurred_at, actor_type, actor_id, action, target_type, target_id, severity, details FROM cotani_audit_entries WHERE 1 = 1");
        query.action().ifPresent(value -> sql.append(" AND action = ?"));
        query.actor().ifPresent(value -> sql.append(" AND actor_type = ? AND actor_id = ?"));
        query.target().ifPresent(value -> sql.append(" AND target_type = ? AND target_id = ?"));
        query.from().ifPresent(value -> sql.append(" AND occurred_at >= ?"));
        query.until().ifPresent(value -> sql.append(" AND occurred_at <= ?"));
        query.before().ifPresent(value -> sql.append(" AND (occurred_at < ? OR (occurred_at = ? AND id < ?))"));
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");

        return storage.queryExecutor()
                .queryMany(sql.toString(), binder -> bindQuery(binder, query), StorageAuditRepository::mapEntry);
    }

    public static List<Migration> migrations() {
        return List.of(new CreateAuditTablesMigration(), new CreateAuditIndexesMigration());
    }

    private static void bindQuery(com.cotani.storage.query.ParameterBinder binder, AuditQuery query)
            throws SQLException {
        if (query.action().isPresent()) {
            binder.string(query.action().orElseThrow().value());
        }
        if (query.actor().isPresent()) {
            var actor = query.actor().orElseThrow();
            binder.string(actor.type()).string(actor.id());
        }
        if (query.target().isPresent()) {
            var target = query.target().orElseThrow();
            binder.string(target.type()).string(target.id());
        }
        if (query.from().isPresent()) {
            binder.instant(query.from().orElseThrow());
        }
        if (query.until().isPresent()) {
            binder.instant(query.until().orElseThrow());
        }
        if (query.before().isPresent()) {
            AuditCursor cursor = query.before().orElseThrow();
            binder.instant(cursor.occurredAt())
                    .instant(cursor.occurredAt())
                    .string(cursor.id().toString());
        }
        binder.integer(query.limit());
    }

    private static AuditEntry mapEntry(Row row) throws SQLException {
        return new AuditEntry(
                java.util.UUID.fromString(row.getString("id")),
                row.getInstantOptional("occurred_at")
                        .orElseThrow(() -> new IllegalStateException("Audit occurred_at is null")),
                AuditActor.of(row.getString("actor_type"), row.getString("actor_id")),
                AuditAction.of(row.getString("action")),
                AuditTarget.of(row.getString("target_type"), row.getString("target_id")),
                AuditSeverity.valueOf(row.getString("severity")),
                AuditDetailsCodec.decode(row.getString("details")));
    }
}
