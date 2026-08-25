package com.cotani.audit.api;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Persistence SPI for append-only, idempotent audit entries. */
public interface AuditRepository {
    /**
     * Persists one immutable entry without blocking the caller.
     *
     * <p>Implementations must treat an existing entry with the same ID as a successful no-op.
     */
    CompletionStage<Void> appendAsync(AuditEntry entry);

    /** Returns at most {@link AuditQuery#limit()} entries ordered newest first. */
    CompletionStage<List<AuditEntry>> queryAsync(AuditQuery query);
}
