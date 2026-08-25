package com.cotani.audit.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stable cursor pointing immediately after an audit entry in newest-first ordering. */
public record AuditCursor(Instant occurredAt, UUID id) {
    public AuditCursor {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(id, "id");
    }

    public static AuditCursor after(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return new AuditCursor(entry.occurredAt(), entry.id());
    }
}
